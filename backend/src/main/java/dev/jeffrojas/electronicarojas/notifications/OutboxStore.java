package dev.jeffrojas.electronicarojas.notifications;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import dev.jeffrojas.electronicarojas.customers.ContactChannel;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.AttemptOutcome;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SkipReason;

/**
 * Guarded SQL state changes of the outbox. Every write after the claim is fenced by
 * {@code status = 'SENDING' AND locked_by = :worker}: a worker whose lease expired (and whose
 * message another worker took over) cannot overwrite the newer result.
 */
@Component
class OutboxStore {

	private final JdbcClient jdbc;

	OutboxStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Claims up to {@code batch} due messages for one worker. {@code FOR UPDATE SKIP LOCKED} lets
	 * several workers (threads or instances) poll at once: each row goes to exactly one of them, and
	 * nobody waits for rows another worker is claiming. A SENDING row whose lease expired (its worker
	 * died mid-send) is claimable again: delivery is at-least-once, never silently lost.
	 */
	List<Long> claim(String workerId, Instant now, Instant leaseUntil, int batch) {
		return jdbc.sql("""
				UPDATE notification_outbox o
				SET status = 'SENDING', attempts = o.attempts + 1, locked_by = :worker, locked_until = :leaseUntil,
				    updated_at = :now
				WHERE o.id IN (
				    SELECT id FROM notification_outbox
				    WHERE (status = 'PENDING' AND next_attempt_at <= :now)
				       OR (status = 'SENDING' AND locked_until < :now)
				    ORDER BY next_attempt_at, id
				    LIMIT :batch
				    FOR UPDATE SKIP LOCKED)
				RETURNING o.id
				""")
			.param("worker", workerId)
			.param("leaseUntil", Timestamp.from(leaseUntil))
			.param("now", Timestamp.from(now))
			.param("batch", batch)
			.query(Long.class)
			.list();
	}

	boolean markSent(long id, String workerId, Instant now, String recipientHint, String providerMessageId) {
		return jdbc.sql("""
				UPDATE notification_outbox
				SET status = 'SENT', sent_at = :now, updated_at = :now, locked_by = NULL, locked_until = NULL,
				    last_error = NULL, recipient_hint = :hint, provider_message_id = :providerId
				WHERE id = :id AND status = 'SENDING' AND locked_by = :worker
				""")
			.param("now", Timestamp.from(now))
			.param("hint", recipientHint)
			.param("providerId", providerMessageId)
			.param("id", id)
			.param("worker", workerId)
			.update() == 1;
	}

	boolean markRetry(long id, String workerId, Instant now, Instant nextAttemptAt, String error, String recipientHint) {
		return jdbc.sql("""
				UPDATE notification_outbox
				SET status = 'PENDING', next_attempt_at = :next, updated_at = :now, locked_by = NULL,
				    locked_until = NULL, last_error = :error, recipient_hint = :hint
				WHERE id = :id AND status = 'SENDING' AND locked_by = :worker
				""")
			.param("next", Timestamp.from(nextAttemptAt))
			.param("now", Timestamp.from(now))
			.param("error", error)
			.param("hint", recipientHint)
			.param("id", id)
			.param("worker", workerId)
			.update() == 1;
	}

	boolean markFailed(long id, String workerId, Instant now, String error, String recipientHint) {
		return jdbc.sql("""
				UPDATE notification_outbox
				SET status = 'FAILED', updated_at = :now, locked_by = NULL, locked_until = NULL, last_error = :error,
				    recipient_hint = coalesce(:hint, recipient_hint)
				WHERE id = :id AND status = 'SENDING' AND locked_by = :worker
				""")
			.param("now", Timestamp.from(now))
			.param("error", error)
			.param("hint", recipientHint)
			.param("id", id)
			.param("worker", workerId)
			.update() == 1;
	}

	boolean markSkipped(long id, String workerId, Instant now, SkipReason reason) {
		return jdbc.sql("""
				UPDATE notification_outbox
				SET status = 'SKIPPED', skip_reason = :reason, updated_at = :now, locked_by = NULL, locked_until = NULL
				WHERE id = :id AND status = 'SENDING' AND locked_by = :worker
				""")
			.param("reason", reason.name())
			.param("now", Timestamp.from(now))
			.param("id", id)
			.param("worker", workerId)
			.update() == 1;
	}

	void recordAttempt(long id, int attemptNumber, String workerId, AttemptOutcome outcome, String errorCode,
			String errorMessage, Instant startedAt, Instant finishedAt) {
		jdbc.sql("""
				INSERT INTO notification_attempts (outbox_id, attempt_number, worker_id, outcome, error_code,
				    error_message, started_at, finished_at)
				VALUES (:id, :attempt, :worker, :outcome, :code, :message, :started, :finished)
				""")
			.param("id", id)
			.param("attempt", attemptNumber)
			.param("worker", workerId)
			.param("outcome", outcome.name())
			.param("code", errorCode)
			.param("message", errorMessage)
			.param("started", Timestamp.from(startedAt))
			.param("finished", Timestamp.from(finishedAt))
			.update();
	}

	/** A withdrawn consent stops the customer's waiting messages on that channel at once. */
	int skipPending(long customerId, ContactChannel channel, Instant now) {
		return jdbc.sql("""
				UPDATE notification_outbox
				SET status = 'SKIPPED', skip_reason = 'CONSENT_WITHDRAWN', updated_at = :now
				WHERE customer_id = :customer AND channel = :channel AND status = 'PENDING'
				""")
			.param("now", Timestamp.from(now))
			.param("customer", customerId)
			.param("channel", channel.name())
			.update();
	}

	/** Manual retry of a FAILED message: one more attempt, due now. */
	boolean retry(long id, Instant now) {
		return jdbc.sql("""
				UPDATE notification_outbox
				SET status = 'PENDING', next_attempt_at = :now, max_attempts = attempts + 1, updated_at = :now
				WHERE id = :id AND status = 'FAILED'
				""")
			.param("now", Timestamp.from(now))
			.param("id", id)
			.update() == 1;
	}

}
