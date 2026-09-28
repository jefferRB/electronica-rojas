package dev.jeffrojas.electronicarojas.notifications;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CustomerContact;
import dev.jeffrojas.electronicarojas.customers.CustomerService;
import dev.jeffrojas.electronicarojas.notifications.MailTransport.MailDeliveryException;
import dev.jeffrojas.electronicarojas.notifications.MailTransport.OutgoingMail;
import dev.jeffrojas.electronicarojas.notifications.NotificationComposer.ComposedMessage;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.AttemptOutcome;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SkipReason;

/**
 * The outbox worker (C.3). Each run:
 * <ol>
 * <li>claims due messages in a short transaction ({@code FOR UPDATE SKIP LOCKED}, lease);</li>
 * <li>for each one, OUTSIDE any transaction: re-checks consent and address, composes the text and
 * hands it to the transport;</li>
 * <li>records the result in another short transaction, fenced by its lease.</li>
 * </ol>
 * No database transaction is open while talking to the mail server, so a slow provider never holds
 * row locks, and a business transaction never waits for, or is rolled back by, e-mail.
 * <p>
 * Retries: transient errors wait 1, 5, 15, then 60 minutes, up to {@code maxAttempts}; permanent
 * errors fail at once. Guarantee: at-least-once towards the provider, never before the business
 * commit, and never for a consent withdrawn before the claim.
 */
@Component
public class NotificationDispatcher {

	private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

	private static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
			Duration.ofMinutes(15), Duration.ofMinutes(60));

	private final OutboxStore store;

	private final OutboxMessageRepository messages;

	private final NotificationOutbox outbox;

	private final CustomerService customers;

	private final MailTransport transport;

	private final NotificationProperties properties;

	private final TransactionTemplate transaction;

	private final Clock clock;

	private final String workerId;

	NotificationDispatcher(OutboxStore store, OutboxMessageRepository messages, NotificationOutbox outbox,
			CustomerService customers, MailTransport transport, NotificationProperties properties,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.store = store;
		this.messages = messages;
		this.outbox = outbox;
		this.customers = customers;
		this.transport = transport;
		this.properties = properties;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
		this.workerId = workerName();
	}

	@Scheduled(initialDelayString = "PT10S", fixedDelayString = "${app.notifications.poll-interval:PT15S}")
	void scheduledRun() {
		try {
			runOnce();
		}
		catch (RuntimeException ex) {
			// Never let one failed run stop the schedule; the next run retries.
			log.warn("Notification worker run failed: {}", ex.getClass().getSimpleName());
		}
	}

	/** One worker run; returns how many messages it processed. Safe to call from several threads. */
	public int runOnce() {
		Instant now = clock.instant();
		List<Long> claimed = transaction
			.execute(status -> store.claim(workerId, now, now.plus(properties.lease()), properties.batchSize()));
		for (Long id : claimed) {
			deliver(id);
		}
		return claimed.size();
	}

	private void deliver(long id) {
		Instant started = clock.instant();
		OutboxMessage message = messages.findById(id).orElseThrow();
		int attempt = message.getAttempts();
		if (attempt > message.getMaxAttempts()) {
			// Its previous worker died after the last allowed attempt: give up instead of looping.
			finish(id, attempt, started, AttemptOutcome.FAILED, "MAX_ATTEMPTS", "Maximum attempts reached", null, null);
			return;
		}
		// Consent and address are re-validated now: a withdrawal after the event stops the message.
		SkipReason skip = outbox.skipReason(message.getCustomerId(), message.getChannel());
		CustomerContact contact = customers.contactOf(message.getCustomerId()).orElse(null);
		if (skip != null || contact == null) {
			SkipReason reason = skip == SkipReason.NO_CONSENT ? SkipReason.CONSENT_WITHDRAWN
					: skip == null ? SkipReason.NO_ADDRESS : skip;
			transaction.executeWithoutResult(status -> {
				store.markSkipped(id, workerId, clock.instant(), reason);
				store.recordAttempt(id, attempt, workerId, AttemptOutcome.SKIPPED, reason.name(), null, started,
						clock.instant());
			});
			log.info("Notification {} skipped: {}", id, reason);
			return;
		}
		String hint = mask(contact.email());
		try {
			ComposedMessage composed = NotificationComposer.compose(message.getEventType(), message.getPayload(),
					contact.fullName(), properties.publicBaseUrl());
			String providerId = transport.send(new OutgoingMail(contact.email(), composed.subject(), composed.body(),
					"<outbox-" + id + "@electronica-rojas.notifications>"));
			transaction.executeWithoutResult(status -> {
				if (!store.markSent(id, workerId, clock.instant(), hint, providerId)) {
					log.warn("Notification {} was sent but its lease had expired", id);
				}
				store.recordAttempt(id, attempt, workerId, AttemptOutcome.SENT, null, null, started, clock.instant());
			});
			log.info("Notification {} sent (attempt {})", id, attempt);
		}
		catch (MailDeliveryException ex) {
			boolean last = ex.isPermanent() || attempt >= message.getMaxAttempts();
			finish(id, attempt, started, last ? AttemptOutcome.FAILED : AttemptOutcome.RETRY, ex.code(), ex.getMessage(),
					hint, last ? null : clock.instant().plus(backoff(attempt)));
			log.warn("Notification {} attempt {} failed: {}{}", id, attempt, ex.code(), last ? " (final)" : "");
		}
		catch (RuntimeException ex) {
			boolean last = attempt >= message.getMaxAttempts();
			finish(id, attempt, started, last ? AttemptOutcome.FAILED : AttemptOutcome.RETRY, "INTERNAL_ERROR",
					ex.getClass().getSimpleName(), hint, last ? null : clock.instant().plus(backoff(attempt)));
			log.warn("Notification {} attempt {} failed: {}", id, attempt, ex.getClass().getSimpleName());
		}
	}

	private void finish(long id, int attempt, Instant started, AttemptOutcome outcome, String code, String error,
			String hint, Instant nextAttempt) {
		transaction.executeWithoutResult(status -> {
			Instant now = clock.instant();
			if (outcome == AttemptOutcome.RETRY) {
				store.markRetry(id, workerId, now, nextAttempt, error, hint);
			}
			else {
				store.markFailed(id, workerId, now, error, hint);
			}
			store.recordAttempt(id, attempt, workerId, outcome, code, error, started, now);
		});
	}

	static Duration backoff(int attempt) {
		return BACKOFF.get(Math.min(Math.max(attempt, 1), BACKOFF.size()) - 1);
	}

	/** "l***@ejemplo.test": enough to recognize the address, never the whole of it. */
	static String mask(String email) {
		if (email == null) {
			return null;
		}
		int at = email.indexOf('@');
		return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at).toLowerCase(Locale.ROOT);
	}

	private static String workerName() {
		String host;
		try {
			host = InetAddress.getLocalHost().getHostName();
		}
		catch (Exception ex) {
			host = "worker";
		}
		host = host.length() > 60 ? host.substring(0, 60) : host;
		return host + "-" + UUID.randomUUID().toString().substring(0, 8);
	}

}
