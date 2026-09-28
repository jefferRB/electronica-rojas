package dev.jeffrojas.electronicarojas.notifications;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.customers.ConsentChanged;
import dev.jeffrojas.electronicarojas.customers.ContactChannel;
import dev.jeffrojas.electronicarojas.customers.CustomerConsentService;
import dev.jeffrojas.electronicarojas.customers.CustomerService;
import dev.jeffrojas.electronicarojas.notifications.NotificationDtos.NotificationStatus;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SkipReason;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.Status;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SubjectType;

/**
 * Entry point of the transactional outbox (BR-NOT-002). Business use cases call
 * {@link #enqueue} inside their own transaction: the message is stored with the business change or
 * not at all, and nothing is sent until that transaction has committed and a worker picks it up.
 * <p>
 * One row per event, channel and customer ({@code dedupe_key}, UNIQUE). A row is created even when
 * the customer has not consented, as SKIPPED, so staff can see that the customer was NOT notified
 * and call instead (FR-REP-004: notification status shown apart from the business status).
 */
@Service
public class NotificationOutbox {

	/** Channels with a real transport today. WhatsApp consent is recorded but not delivered yet. */
	static final Set<ContactChannel> DELIVERABLE_CHANNELS = Set.of(ContactChannel.EMAIL);

	private final OutboxMessageRepository messages;

	private final OutboxStore store;

	private final CustomerConsentService consents;

	private final CustomerService customers;

	private final NotificationProperties properties;

	private final Clock clock;

	NotificationOutbox(OutboxMessageRepository messages, OutboxStore store, CustomerConsentService consents,
			CustomerService customers, NotificationProperties properties, Clock clock) {
		this.messages = messages;
		this.store = store;
		this.consents = consents;
		this.customers = customers;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void enqueue(NotificationRequest request) {
		Instant now = clock.instant();
		for (ContactChannel channel : DELIVERABLE_CHANNELS) {
			String dedupeKey = request.eventType() + ":" + request.sourceEventKey() + ":" + channel + ":"
					+ request.customerId();
			if (messages.existsByDedupeKey(dedupeKey)) {
				continue;
			}
			SkipReason skip = skipReason(request.customerId(), channel);
			messages.save(new OutboxMessage(dedupeKey, request, channel, skip == null ? Status.PENDING : Status.SKIPPED,
					skip, properties.maxAttempts(), now));
		}
	}

	/**
	 * Same transaction as the withdrawal: once it commits, no pending message of that channel is
	 * delivered. A message already claimed by a worker is re-checked by the worker before sending.
	 */
	@EventListener
	void onConsentChanged(ConsentChanged event) {
		if (!event.granted()) {
			store.skipPending(event.customerId(), event.channel(), clock.instant());
		}
	}

	/** Notification status of records (an order, the visits of a request) for their screens. */
	@Transactional(readOnly = true)
	public List<NotificationStatus> statusFor(SubjectType subjectType, Collection<Long> subjectIds) {
		if (subjectIds.isEmpty()) {
			return List.of();
		}
		return messages.findBySubjectTypeAndSubjectIdInOrderByCreatedAtAscIdAsc(subjectType, subjectIds)
			.stream()
			.map(NotificationStatus::from)
			.toList();
	}

	/** Consent and address as of now; null when the message may be sent. */
	SkipReason skipReason(long customerId, ContactChannel channel) {
		if (!consents.isGranted(customerId, channel)) {
			return SkipReason.NO_CONSENT;
		}
		boolean hasAddress = customers.contactOf(customerId)
			.map(contact -> channel == ContactChannel.EMAIL ? contact.email() != null : contact.phone() != null)
			.orElse(false);
		return hasAddress ? null : SkipReason.NO_ADDRESS;
	}

}
