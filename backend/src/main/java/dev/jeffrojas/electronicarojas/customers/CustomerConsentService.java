package dev.jeffrojas.electronicarojas.customers;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.ConsentGrant;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.ConsentState;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.ConsentStatement;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CustomerConsents;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.RecordConsentRequest;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * Notification consent per channel (BR-CUS-001, BR-CUS-006): the single place that records and
 * answers "may we send this customer a notification on this channel?".
 * <ul>
 * <li>A consent is an explicit statement with source, date and text version; giving an e-mail or a
 * phone never implies it, and existing customers start without consent.</li>
 * <li>Statements are append-only; the current consent is the latest one.</li>
 * <li>Every statement publishes {@link ConsentChanged} in the same transaction, so pending
 * notifications stop as soon as a withdrawal commits.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class CustomerConsentService {

	private static final String NOT_FOUND = "Customer not found.";

	private final CustomerRepository customers;

	private final CustomerConsentRepository consents;

	private final AuditService audit;

	private final ApplicationEventPublisher events;

	private final EntityManager entityManager;

	private final Clock clock;

	CustomerConsentService(CustomerRepository customers, CustomerConsentRepository consents, AuditService audit,
			ApplicationEventPublisher events, EntityManager entityManager, Clock clock) {
		this.customers = customers;
		this.consents = consents;
		this.audit = audit;
		this.events = events;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public CustomerConsents consents(CurrentUser user, long customerId) {
		return view(requireVisible(user, customerId));
	}

	/**
	 * The authorized flow to grant or withdraw a channel (customer detail). Staff records what the
	 * customer said at the counter, by phone or in writing; the public form has its own path.
	 */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	@Transactional
	public CustomerConsents record(CurrentUser user, long customerId, RecordConsentRequest request) {
		if (request.source() == ConsentSource.PUBLIC_FORM) {
			throw new InvalidRequestException("source", "CONSENT_SOURCE_INVALID",
					"public form consents are applied from the service request");
		}
		Customer customer = requireVisible(user, customerId);
		requireCurrentText(request.granted(), request.textVersion());
		recordStatement(user, customer, request.channel(), request.granted(), request.source(), request.textVersion(),
				clock.instant(), null);
		return view(customer);
	}

	/** Consents stated while registering a customer at the counter or by phone (reception form). */
	@Transactional(propagation = Propagation.MANDATORY)
	void recordGrant(CurrentUser user, Customer customer, ConsentGrant grant) {
		if (grant == null || grant.channels() == null) {
			return;
		}
		if (grant.source() == ConsentSource.PUBLIC_FORM) {
			throw new InvalidRequestException("consent.source", "CONSENT_SOURCE_INVALID",
					"public form consents are applied from the service request");
		}
		requireCurrentText(true, grant.textVersion());
		Instant now = clock.instant();
		for (ContactChannel channel : grant.channels().stream().distinct().toList()) {
			recordStatement(user, customer, channel, true, grant.source(), grant.textVersion(), now, null);
		}
	}

	/**
	 * Stores one statement. Validates that a grant names the current text and that e-mail consent
	 * has an address to send to; audits codes only (no personal data).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordStatement(CurrentUser user, Customer customer, ContactChannel channel, boolean granted,
			ConsentSource source, String textVersion, Instant statedAt, String reference) {
		if (granted && (textVersion == null || textVersion.isBlank())) {
			throw new InvalidRequestException("textVersion", "NotBlank", "a granted consent names the accepted text");
		}
		if (granted && channel == ContactChannel.EMAIL && customer.getEmail() == null) {
			throw new ConflictException("EMAIL_REQUIRED", "Register the customer's e-mail before accepting e-mail notices.");
		}
		consents.saveAndFlush(new CustomerConsent(customer, channel, granted, source, textVersion,
				reference, statedAt, entityManager.getReference(AppUser.class, user.id()), clock.instant()));
		audit.record(user, AuditEntry.of(AuditAction.CUSTOMER_CONSENT_RECORDED, customer.getId())
			.branch(customer.getRegisteredBranch().getId())
			.detail("channel", channel)
			.detail("granted", granted)
			.detail("source", source)
			.detail("textVersion", textVersion)
			.detail("reference", reference)
			.summary("Customer " + customer.getId() + " " + (granted ? "granted " : "withdrew ") + channel + " consent"));
		events.publishEvent(new ConsentChanged(customer.getId(), channel, granted));
	}

	/** Staff flows record what the customer accepts today: the current text only. */
	private static void requireCurrentText(boolean granted, String textVersion) {
		if (granted && !NotificationConsentText.CURRENT_VERSION.equals(textVersion)) {
			throw new InvalidRequestException("textVersion", "CONSENT_TEXT_OUTDATED",
					"the accepted text must be version " + NotificationConsentText.CURRENT_VERSION);
		}
	}

	/** For the notification worker (system use): is the channel currently granted? */
	public boolean isGranted(long customerId, ContactChannel channel) {
		return consents.findFirstByCustomerIdAndChannelOrderByStatedAtDescIdDesc(customerId, channel)
			.map(CustomerConsent::isGranted)
			.orElse(false);
	}

	private CustomerConsents view(Customer customer) {
		List<ConsentStatement> history = consents.findHistory(customer.getId())
			.stream()
			.map(CustomerConsentService::statement)
			.toList();
		List<ConsentState> current = Arrays.stream(ContactChannel.values())
			.map(channel -> new ConsentState(channel,
					history.stream().filter(entry -> entry.channel() == channel).findFirst().orElse(null)))
			.toList();
		return new CustomerConsents(customer.getId(), customer.getEmail() != null, NotificationConsentText.CURRENT_VERSION,
				current, history);
	}

	private static ConsentStatement statement(CustomerConsent consent) {
		return new ConsentStatement(consent.getId(), consent.getChannel(), consent.isGranted(), consent.getSource(),
				consent.getTextVersion(), consent.getReference(), consent.getStatedAt(),
				new CustomerDtos.PersonRef(consent.getRecordedBy().getId(), consent.getRecordedBy().getFullName()),
				consent.getRecordedAt());
	}

	private Customer requireVisible(CurrentUser user, long customerId) {
		if (!customers.isVisible(customerId, user.isAdmin(), user.id())) {
			throw new NotFoundException(NOT_FOUND);
		}
		return customers.findById(customerId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
	}

}
