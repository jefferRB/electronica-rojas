package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.customers.Customer;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.NewCustomer;
import dev.jeffrojas.electronicarojas.customers.ConsentSource;
import dev.jeffrojas.electronicarojas.customers.ContactChannel;
import dev.jeffrojas.electronicarojas.customers.CustomerConsentService;
import dev.jeffrojas.electronicarojas.customers.CustomerService;
import dev.jeffrojas.electronicarojas.customers.PhoneNumbers;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ChangeBranchRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.DecisionRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.LinkCustomerRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ServiceRequestDetail;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ServiceRequestSummary;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.StaffRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.EventType;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestChannel;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus;
import dev.jeffrojas.electronicarojas.shared.OperationFingerprint;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * Internal handling of home-service requests (FR-SRV-003): inbox, review, customer linking with
 * identity resolution, branch, rejection and cancellation. Every decision locks the request row
 * and writes the timeline and the audit in the same transaction.
 */
@Service
@Transactional(readOnly = true)
public class ServiceRequestService {

	static final int MAX_PAGE_SIZE = 100;

	private static final List<Long> NO_BRANCHES = List.of(-1L);

	private final ServiceRequestRepository requests;

	private final ServiceVisitRepository visits;

	private final ServiceRequestAccess access;

	private final ServiceViews views;

	private final ServiceTimeline timeline;

	private final BranchService branchService;

	private final CustomerService customerService;

	private final CustomerConsentService consentService;

	private final PublicServiceRequestService publicService;

	private final EntityManager entityManager;

	private final Clock clock;

	ServiceRequestService(ServiceRequestRepository requests, ServiceVisitRepository visits, ServiceRequestAccess access,
			ServiceViews views, ServiceTimeline timeline, BranchService branchService, CustomerService customerService,
			CustomerConsentService consentService, PublicServiceRequestService publicService, EntityManager entityManager, Clock clock) {
		this.requests = requests;
		this.visits = visits;
		this.access = access;
		this.views = views;
		this.timeline = timeline;
		this.branchService = branchService;
		this.customerService = customerService;
		this.consentService = consentService;
		this.publicService = publicService;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	public record RequestFilter(Long branchId, RequestStatus status, Long customerId, String search) {
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public PageResponse<ServiceRequestSummary> list(CurrentUser user, RequestFilter filter, int page, int size) {
		boolean restrict = !user.isAdmin();
		List<Long> branchIds = restrict ? branchService.listSelectable(user).stream().map(BranchSummary::id).toList()
				: NO_BRANCHES;
		Page<ServiceRequest> result = requests.search(restrict, branchIds.isEmpty() ? NO_BRANCHES : branchIds,
				filter.branchId(), filter.status(), filter.customerId(), likePattern(filter.search()),
				PageRequest.of(page, size));
		// Active visit of each row in one extra query (no N+1).
		Map<Long, ServiceVisit> active = result.isEmpty() ? Map.of()
				: visits.findActiveForRequests(result.map(ServiceRequest::getId).getContent())
					.stream()
					.collect(Collectors.toMap(visit -> visit.getRequest().getId(), Function.identity()));
		return PageResponse.of(result, request -> ServiceViews.summary(request, active.get(request.getId())));
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public ServiceRequestDetail get(CurrentUser user, long requestId) {
		return views.detail(user, access.requireRequest(user, requestId));
	}

	/**
	 * A request taken by phone or at the counter. The customer is resolved with the same rules as
	 * reception: an existing customer (one of another branch only if its phone matches) or a new
	 * one created in this transaction. Idempotent by {@code submissionId}.
	 */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	@Transactional
	public ServiceRequestDetail createByStaff(CurrentUser user, StaffRequest form) {
		String phone = PhoneNumbers.normalize(form.contactPhone())
			.orElseThrow(() -> new InvalidRequestException("contactPhone", "PHONE_INVALID", "is not a valid phone number"));
		String fingerprint = OperationFingerprint.of("STAFF_SERVICE_REQUEST", form.branchId(), form.customerId(),
				form.contactName(), phone, form.province(), form.canton(), form.addressLine(), form.deviceType(),
				form.problemDescription(), form.preferredDate());
		requests.lockSubmission(form.submissionId().hashCode());
		Optional<ServiceRequest> replay = requests.findBySubmissionId(form.submissionId());
		if (replay.isPresent()) {
			if (!replay.get().getSubmissionFingerprint().equals(fingerprint)) {
				throw new ConflictException("OPERATION_ID_REUSED", "This submission id was already used for a different request.");
			}
			return views.detail(user, access.requireRequest(user, replay.get().getId()));
		}
		Branch branch = branchService.requireOperable(user, form.branchId());
		Customer customer = resolveCustomer(user, branch, form.customerId(), Boolean.TRUE.equals(form.registerCustomer()),
				Boolean.TRUE.equals(form.confirmedNewPerson()), form.contactName(), phone, form.contactEmail(),
				form.addressLine());

		Instant now = clock.instant();
		ServiceRequest request = requests.saveAndFlush(new ServiceRequest(publicService.nextCode(now), form.submissionId(),
				fingerprint, RequestChannel.STAFF, branch,
				new ServiceRequest.Submission(form.contactName(), phone, form.contactEmail(), form.province(), form.canton(),
						form.district(), form.addressLine(), form.deviceType(), form.brand(), form.model(),
						form.problemDescription(), form.preferredDate(), form.preferredWindow(), form.additionalNotes(),
						Boolean.TRUE.equals(form.notificationsConsent()), false, false, null),
				entityManager.getReference(AppUser.class, user.id()), now));
		timeline.record(user, request, null, EventType.SUBMITTED, null, RequestStatus.PENDING, Map.of("channel", "STAFF"),
				null);
		if (customer != null) {
			request.linkCustomer(customer, now);
			move(user, request, RequestStatus.UNDER_REVIEW, null);
			timeline.record(user, request, null, EventType.CUSTOMER_LINKED, null, null, Map.of("customerId", customer.getId()),
					null);
		}
		return views.detail(user, request);
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	@Transactional
	public ServiceRequestDetail startReview(CurrentUser user, long requestId) {
		ServiceRequest request = access.requireRequestForUpdate(user, requestId);
		if (request.getStatus() != RequestStatus.UNDER_REVIEW) {
			move(user, request, RequestStatus.UNDER_REVIEW, null);
		}
		return views.detail(user, request);
	}

	/**
	 * Identity resolution for a request (Phase 3.1 rules): an existing customer the user can see,
	 * one of another branch whose phone equals the request's, or a new record from the contact data
	 * (POSSIBLE_DUPLICATE_CUSTOMER unless confirmed). Never automatic.
	 */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	@Transactional
	public ServiceRequestDetail linkCustomer(CurrentUser user, long requestId, LinkCustomerRequest link) {
		ServiceRequest request = access.requireRequestForUpdate(user, requestId);
		requireOpen(request);
		if (request.getCustomer() != null) {
			if (link.customerId() != null && link.customerId().equals(request.getCustomer().getId())) {
				return views.detail(user, request);
			}
			throw new ConflictException("CUSTOMER_ALREADY_LINKED", "The request is already linked to a customer.");
		}
		Customer customer = resolveCustomer(user, request.getBranch(), link.customerId(),
				Boolean.TRUE.equals(link.registerNew()), Boolean.TRUE.equals(link.confirmedNewPerson()),
				request.getContactName(), request.getContactPhone(), request.getContactEmail(), request.getAddressLine());
		if (customer == null) {
			throw new InvalidRequestException("customerId", "CUSTOMER_REQUIRED", "choose a customer or register a new one");
		}
		request.linkCustomer(customer, clock.instant());
		applyFormConsents(user, request, customer);
		if (request.getStatus() == RequestStatus.PENDING) {
			move(user, request, RequestStatus.UNDER_REVIEW, null);
		}
		timeline.record(user, request, null, EventType.CUSTOMER_LINKED, null, null, Map.of("customerId", customer.getId()),
				null);
		return views.detail(user, request);
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
	@Transactional
	public ServiceRequestDetail changeBranch(CurrentUser user, long requestId, ChangeBranchRequest change) {
		ServiceRequest request = access.requireRequestForUpdate(user, requestId);
		requireOpen(request);
		if (visits.findActiveForRequest(request.getId()).isPresent()) {
			throw new ConflictException("VISIT_ACTIVE", "Cancel the scheduled visit before moving the request to another branch.");
		}
		Branch target = branchService.activeBranches()
			.stream()
			.filter(branch -> branch.getId().equals(change.branchId()))
			.findFirst()
			.orElseThrow(() -> new InvalidRequestException("branchId", "BRANCHES_INVALID", "unknown or inactive branch"));
		if (target.getId().equals(request.getBranch().getId())) {
			return views.detail(user, request);
		}
		Branch previous = request.getBranch();
		request.assignBranch(target, clock.instant());
		timeline.record(user, request, null, EventType.BRANCH_CHANGED, null, null,
				Map.of("previousBranchCode", previous.getCode(), "previousBranchName", previous.getName()), null);
		// The caller may have moved it out of their own scope; answer with what they can still see.
		return access.canSeeRequest(user, request) ? views.detail(user, request) : null;
	}

	/** Declines a request that has no confirmed visit; a proposed visit is withdrawn with it. */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	@Transactional
	public ServiceRequestDetail reject(CurrentUser user, long requestId, DecisionRequest decision) {
		ServiceRequest request = access.requireRequestForUpdate(user, requestId);
		if (!ServicePolicy.canMove(request.getStatus(), RequestStatus.REJECTED)) {
			throw invalidTransition(request.getStatus(), RequestStatus.REJECTED);
		}
		String reason = decision.reason().strip();
		withdrawActiveVisit(user, request, reason);
		move(user, request, RequestStatus.REJECTED, reason);
		return views.detail(user, request);
	}

	/** Cancels on the customer's behalf; releases a proposed or confirmed visit. */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	@Transactional
	public ServiceRequestDetail cancel(CurrentUser user, long requestId, DecisionRequest decision) {
		ServiceRequest request = access.requireRequestForUpdate(user, requestId);
		if (!ServicePolicy.canMove(request.getStatus(), RequestStatus.CANCELLED)) {
			throw invalidTransition(request.getStatus(), RequestStatus.CANCELLED);
		}
		List<ServiceVisit> requestVisits = visits.findForRequest(request.getId());
		if (requestVisits.stream().anyMatch(v -> v.getStatus() == VisitStatus.IN_PROGRESS || v.getStatus() == VisitStatus.COMPLETED)) {
			throw new ConflictException("VISIT_ALREADY_STARTED", "The visit already started; it can no longer be cancelled.");
		}
		String reason = decision.reason().strip();
		withdrawActiveVisit(user, request, reason);
		move(user, request, RequestStatus.CANCELLED, reason);
		return views.detail(user, request);
	}

	// ---- shared with VisitService ----

	/** Changes the request status after checking the table; writes timeline + audit. */
	void move(CurrentUser user, ServiceRequest request, RequestStatus target, String reason) {
		RequestStatus from = request.getStatus();
		if (from == target) {
			return;
		}
		if (!ServicePolicy.canMove(from, target)) {
			throw invalidTransition(from, target);
		}
		request.moveTo(target, reason, clock.instant());
		EventType type = switch (target) {
			case UNDER_REVIEW -> EventType.REVIEW_STARTED;
			case REJECTED -> EventType.REJECTED;
			case CANCELLED -> EventType.CANCELLED;
			default -> null;
		};
		if (type != null) {
			timeline.record(user, request, null, type, from, target, null, reason);
		}
	}

	static void requireOpen(ServiceRequest request) {
		if (!ServicePolicy.isOpen(request.getStatus())) {
			throw new ConflictException("REQUEST_CLOSED", "The request is " + request.getStatus() + ".",
					Map.of("status", request.getStatus().name()));
		}
	}

	static ConflictException invalidTransition(Enum<?> from, Enum<?> to) {
		return new ConflictException("INVALID_TRANSITION", "Transition " + from + " -> " + to + " is not allowed.",
				Map.of("fromStatus", from.name(), "toStatus", to.name()));
	}

	private void withdrawActiveVisit(CurrentUser user, ServiceRequest request, String reason) {
		visits.findActiveForRequest(request.getId()).ifPresent(visit -> {
			VisitStatus from = visit.getStatus();
			visit.cancel(reason, clock.instant());
			timeline.record(user, request, visit, EventType.VISIT_CANCELLED, from, VisitStatus.CANCELLED, null, reason);
		});
	}

	/**
	 * BR-CUS-006: the channel opt-ins of a public form become the customer's consent when staff links
	 * the request, dated when the customer submitted it and referencing the request. Only for the
	 * same address: an e-mail consent given for another address than the one on file is not applied
	 * (staff confirms it in the customer's record instead). Never inferred from a bare e-mail.
	 */
	private void applyFormConsents(CurrentUser user, ServiceRequest request, Customer customer) {
		if (request.getChannel() != RequestChannel.PUBLIC_FORM) {
			return;
		}
		if (request.isEmailConsent() && customer.getEmail() != null
				&& customer.getEmail().equalsIgnoreCase(request.getContactEmail())) {
			consentService.recordStatement(user, customer, ContactChannel.EMAIL, true, ConsentSource.PUBLIC_FORM,
					request.getConsentTextVersion(), request.getCreatedAt(), request.getRequestCode());
		}
		if (request.isWhatsappConsent() && customer.getPhone().equals(request.getContactPhone())) {
			consentService.recordStatement(user, customer, ContactChannel.WHATSAPP, true, ConsentSource.PUBLIC_FORM,
					request.getConsentTextVersion(), request.getCreatedAt(), request.getRequestCode());
		}
	}

	private Customer resolveCustomer(CurrentUser user, Branch branch, Long customerId, boolean registerNew,
			boolean confirmedNewPerson, String name, String phone, String email, String address) {
		if (customerId != null) {
			return customerService.requireForIntake(user, customerId, phone);
		}
		if (registerNew) {
			return customerService.register(user, branch,
					new NewCustomer(name, phone, email, address, null, confirmedNewPerson ? Boolean.TRUE : null, null));
		}
		return null;
	}

	private static String likePattern(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		return "%" + text.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
	}

}
