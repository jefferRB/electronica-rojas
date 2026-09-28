package dev.jeffrojas.electronicarojas.notifications;

import java.time.Instant;
import java.util.List;

import dev.jeffrojas.electronicarojas.customers.ContactChannel;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.AttemptOutcome;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.EventType;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SkipReason;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.Status;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SubjectType;

/** HTTP contracts of the notifications module. No message body and no full address ever leave here. */
public final class NotificationDtos {

	private NotificationDtos() {
	}

	/** Notification state shown on the order or request it belongs to. */
	public record NotificationStatus(long id, EventType eventType, ContactChannel channel, Status status,
			SkipReason skipReason, int attempts, long subjectId, Instant createdAt, Instant sentAt) {

		static NotificationStatus from(OutboxMessage message) {
			return new NotificationStatus(message.getId(), message.getEventType(), message.getChannel(),
					message.getStatus(), message.getSkipReason(), message.getAttempts(), message.getSubjectId(),
					message.getCreatedAt(), message.getSentAt());
		}

	}

	/** Row of the administration list (FR-NOT-001: failures and retries). */
	public record NotificationRow(long id, EventType eventType, ContactChannel channel, Status status,
			SkipReason skipReason, SubjectType subjectType, long subjectId, String reference, long branchId,
			String recipientHint, int attempts, int maxAttempts, Instant nextAttemptAt, String lastError,
			Instant createdAt, Instant sentAt) {

		static NotificationRow from(OutboxMessage message) {
			return new NotificationRow(message.getId(), message.getEventType(), message.getChannel(), message.getStatus(),
					message.getSkipReason(), message.getSubjectType(), message.getSubjectId(), message.reference(),
					message.getBranchId(), message.getRecipientHint(), message.getAttempts(), message.getMaxAttempts(),
					message.getNextAttemptAt(), message.getLastError(), message.getCreatedAt(), message.getSentAt());
		}

	}

	public record AttemptView(int attemptNumber, String workerId, AttemptOutcome outcome, String errorCode,
			String errorMessage, Instant startedAt, Instant finishedAt) {

		static AttemptView from(NotificationAttempt attempt) {
			return new AttemptView(attempt.getAttemptNumber(), attempt.getWorkerId(), attempt.getOutcome(),
					attempt.getErrorCode(), attempt.getErrorMessage(), attempt.getStartedAt(), attempt.getFinishedAt());
		}

	}

	public record NotificationDetail(NotificationRow message, List<AttemptView> attempts) {
	}

	/** A message of the development inbox (content visible to ADMIN only, and only in inbox mode). */
	public record InboxMessage(String messageId, String to, String subject, String body, Instant acceptedAt) {
	}

}
