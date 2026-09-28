package dev.jeffrojas.electronicarojas.notifications;

/** Value enums of the notifications module (ER-BR-001 section 9, v1.4). */
public final class NotificationEnums {

	private NotificationEnums() {
	}

	/** Business events that notify the customer (C.2); each maps to one template. */
	public enum EventType {
		REPAIR_READY_FOR_PICKUP, VISIT_CONFIRMED, VISIT_RESCHEDULED, VISIT_CANCELLED
	}

	/** The record a notification is about (for its status on that record's screen). */
	public enum SubjectType {
		REPAIR_ORDER, SERVICE_VISIT
	}

	public enum Status {
		/** Waiting for the worker (possibly after a failed attempt, until next_attempt_at). */
		PENDING,
		/** Claimed by a worker until locked_until. */
		SENDING,
		/** Accepted by the provider (not proof that the customer read it). */
		SENT,
		/** Gave up: permanent error or maximum attempts reached. Can be retried manually. */
		FAILED,
		/** Never sent, on purpose: see {@link SkipReason}. */
		SKIPPED
	}

	public enum SkipReason {
		/** No consent for the channel when the event happened. */
		NO_CONSENT,
		/** Consent withdrawn before the message was delivered. */
		CONSENT_WITHDRAWN,
		/** The customer has no address for the channel. */
		NO_ADDRESS
	}

	public enum AttemptOutcome {
		SENT, RETRY, FAILED, SKIPPED
	}

}
