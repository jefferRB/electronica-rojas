package dev.jeffrojas.electronicarojas.customers;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.customers.Customer.CustomerData;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CreateCustomerRequest;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CustomerContact;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CustomerResponse;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.NewCustomer;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CustomerCandidate;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CustomerLookupResponse;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CustomerMatch;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.MatchReason;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.UpdateCustomerRequest;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * Customer use cases (FR-CUS-001, BR-CUS-001/002). ADMIN sees all customers; BRANCH_MANAGER and
 * RECEPTIONIST only those related to their branches; TECHNICIAN has no customer module (they see a
 * name inside assigned orders only). Personal data is never written to logs or audit details.
 */
@Service
@Transactional(readOnly = true)
public class CustomerService {

	static final int MAX_PAGE_SIZE = 100;

	static final int MAX_LOOKUP_SIZE = 10;

	/** Minimum letters of a name, or digits of a phone, before the incremental lookup searches. */
	static final int MIN_LOOKUP_CHARS = 3;

	private static final String NOT_FOUND = "Customer not found.";

	private final CustomerRepository customers;

	private final CustomerConsentService consents;

	private final BranchService branchService;

	private final AuditService audit;

	private final EntityManager entityManager;

	private final Clock clock;

	CustomerService(CustomerRepository customers, CustomerConsentService consents, BranchService branchService,
			AuditService audit, EntityManager entityManager, Clock clock) {
		this.customers = customers;
		this.consents = consents;
		this.branchService = branchService;
		this.audit = audit;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public PageResponse<CustomerResponse> search(CurrentUser user, String search, int page, int size) {
		String pattern = likePattern(search);
		return PageResponse.of(customers.search(user.isAdmin(), user.id(), pattern, PhoneNumbers.searchDigits(search),
				PageRequest.of(page, size)), CustomerResponse::from);
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public CustomerResponse get(CurrentUser user, long customerId) {
		return CustomerResponse.from(requireVisible(user, customerId));
	}

	/** Exact matches for a phone across the company, with minimal data (see CustomerMatch). */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public List<CustomerMatch> phoneMatches(CurrentUser user, String rawPhone) {
		String phone = normalizePhone("phone", rawPhone);
		return customers.findTop10ByPhoneOrderByIdAsc(phone)
			.stream()
			.map(customer -> new CustomerMatch(customer.getId(), customer.getFullName(),
					customers.isVisible(customer.getId(), user.isAdmin(), user.id()), MatchReason.PHONE))
			.toList();
	}

	/**
	 * Incremental lookup for the customer picker (FR-CUS-002). Searches only once the name has
	 * {@value #MIN_LOOKUP_CHARS}+ letters or the phone {@value #MIN_LOOKUP_CHARS}+ digits.
	 * <ul>
	 * <li>Partial name words and phone fragments search the caller's scope only, so typing
	 * fragments never reveals customers of other branches.</li>
	 * <li>A complete phone number additionally reports customers of other branches with that exact
	 * number, as id and name only (the same rule as {@link #phoneMatches}).</li>
	 * </ul>
	 */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public CustomerLookupResponse lookup(CurrentUser user, String name, String phone, int size) {
		List<String> tokens = SearchText.significantLength(name) >= MIN_LOOKUP_CHARS ? SearchText.tokens(name)
				: List.of();
		String digits = PhoneNumbers.searchDigits(phone);
		if (tokens.isEmpty() && digits == null) {
			return new CustomerLookupResponse(List.of(), 0);
		}
		List<String> patterns = tokens.stream().map(SearchText::containsPattern).toList();
		var page = customers.lookup(user.isAdmin(), user.id(), at(patterns, 0), at(patterns, 1), at(patterns, 2), digits,
				PageRequest.of(0, size));
		List<CustomerCandidate> candidates = new ArrayList<>();
		for (Customer customer : page.getContent()) {
			boolean nameMatch = !tokens.isEmpty() && tokens.stream().allMatch(customer.getSearchName()::contains);
			boolean phoneMatch = digits != null && customer.getPhone().contains(digits);
			candidates.add(CustomerCandidate.inScope(customer, nameMatch, phoneMatch));
		}
		PhoneNumbers.normalize(phone).ifPresent(exact -> {
			for (Customer customer : customers.findTop10ByPhoneOrderByIdAsc(exact)) {
				boolean listed = candidates.stream().anyMatch(candidate -> candidate.id() == customer.getId());
				if (!listed && !customers.isVisible(customer.getId(), user.isAdmin(), user.id())) {
					candidates.add(CustomerCandidate.outOfScope(customer));
				}
			}
		});
		return new CustomerLookupResponse(candidates, page.getTotalElements() - page.getNumberOfElements());
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	@Transactional
	public CustomerResponse create(CurrentUser user, CreateCustomerRequest request) {
		Branch branch = branchService.requireOperable(user, request.branchId());
		return CustomerResponse.from(register(user, branch, request.customer()));
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	@Transactional
	public CustomerResponse update(CurrentUser user, long customerId, UpdateCustomerRequest request) {
		Customer customer = requireVisible(user, customerId);
		if (customer.getVersion() != request.version()) {
			throw new ConflictException("STALE_VERSION", "The customer was modified by someone else. Reload and try again.");
		}
		customer.update(new CustomerData(request.fullName(), normalizePhone("phone", request.phone()), request.email(),
				request.address(), request.internalNotes()), clock.instant());
		customers.saveAndFlush(customer);
		audit.record(user, AuditEntry.of(AuditAction.CUSTOMER_UPDATED, customer.getId())
			.branch(customer.getRegisteredBranch().getId())
			.summary("Customer " + customer.getId() + " updated"));
		return CustomerResponse.from(customer);
	}

	/**
	 * For the reception form: registers a new customer inside the caller's transaction, so an order
	 * that fails afterwards leaves no customer behind.
	 * <p>
	 * Identity resolution is re-checked here on every save, whatever the UI showed: customers with
	 * the same phone (company-wide, minimal data) or the same normalized name (caller's scope) are
	 * reported as POSSIBLE_DUPLICATE_CUSTOMER unless the user confirmed it is a different person
	 * ({@code allowDuplicatePhone}). Records are never merged automatically (BR-CUS-002).
	 */
	@Transactional
	public Customer register(CurrentUser user, Branch branch, NewCustomer data) {
		String phone = normalizePhone("phone", data.phone());
		if (!Boolean.TRUE.equals(data.allowDuplicatePhone())) {
			List<CustomerMatch> matches = possibleDuplicates(user, data.fullName(), phone);
			if (!matches.isEmpty()) {
				throw new ConflictException("POSSIBLE_DUPLICATE_CUSTOMER",
						"Customers with the same phone or name already exist. Confirm to create another one.",
						Map.of("matches", matches));
			}
		}
		Customer customer = customers.saveAndFlush(new Customer(
				new CustomerData(data.fullName(), phone, data.email(), data.address(), data.internalNotes()), branch,
				entityManager.getReference(AppUser.class, user.id()), clock.instant()));
		audit.record(user, AuditEntry.of(AuditAction.CUSTOMER_CREATED, customer.getId())
			.branch(branch.getId())
			.detail("branchCode", branch.getCode())
			.detail("branchName", branch.getName())
			.summary("Customer " + customer.getId() + " registered at " + branch.getCode()));
		consents.recordGrant(user, customer, data.consent());
		return customer;
	}

	private List<CustomerMatch> possibleDuplicates(CurrentUser user, String fullName, String phone) {
		var byId = new LinkedHashMap<Long, CustomerMatch>();
		for (Customer customer : customers.findTop10ByPhoneOrderByIdAsc(phone)) {
			byId.put(customer.getId(), new CustomerMatch(customer.getId(), customer.getFullName(),
					customers.isVisible(customer.getId(), user.isAdmin(), user.id()), MatchReason.PHONE));
		}
		for (Customer customer : customers.findSameNameInScope(SearchText.normalize(fullName), user.isAdmin(),
				user.id())) {
			byId.merge(customer.getId(), new CustomerMatch(customer.getId(), customer.getFullName(), true, MatchReason.NAME),
					(byPhone, byName) -> new CustomerMatch(byPhone.id(), byPhone.fullName(), true,
							MatchReason.PHONE_AND_NAME));
		}
		return List.copyOf(byId.values());
	}

	/**
	 * Current contact data for system processes such as notification delivery (no user scope: the
	 * caller already holds a legitimate reference to this customer, e.g. an outbox message).
	 */
	public Optional<CustomerContact> contactOf(long customerId) {
		return customers.findById(customerId)
			.map(customer -> new CustomerContact(customer.getId(), customer.getFullName(), customer.getPhone(),
					customer.getEmail()));
	}

	/**
	 * For the reception form: an existing customer may be used if the caller can see them, or if the
	 * caller proves they are talking to that customer by giving the exact phone on file. Otherwise
	 * the same 404 as a missing customer.
	 */
	public Customer requireForIntake(CurrentUser user, long customerId, String phoneProof) {
		Customer customer = customers.findById(customerId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
		if (customers.isVisible(customerId, user.isAdmin(), user.id())) {
			return customer;
		}
		boolean phoneMatches = PhoneNumbers.normalize(phoneProof).map(customer.getPhone()::equals).orElse(false);
		if (!phoneMatches) {
			throw new NotFoundException(NOT_FOUND);
		}
		return customer;
	}

	private Customer requireVisible(CurrentUser user, long customerId) {
		if (!customers.isVisible(customerId, user.isAdmin(), user.id())) {
			throw new NotFoundException(NOT_FOUND);
		}
		return customers.findById(customerId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
	}

	private static String normalizePhone(String field, String raw) {
		return PhoneNumbers.normalize(raw)
			.orElseThrow(() -> new InvalidRequestException(field, "PHONE_INVALID", "is not a valid phone number"));
	}

	private static String likePattern(String text) {
		String normalized = SearchText.normalize(text);
		return normalized == null || normalized.isEmpty() ? null : SearchText.containsPattern(normalized);
	}

	private static String at(List<String> values, int index) {
		return index < values.size() ? values.get(index) : null;
	}

}
