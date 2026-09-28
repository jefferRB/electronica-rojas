package dev.jeffrojas.electronicarojas.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit event for the UI. {@code action} stays the stable English code; the UI translates it and
 * builds the Spanish description from {@code details}.
 *
 * @param detailsSource RECORDED (stored at the time of the action), LEGACY (read from the summary
 * of an event written before structured details existed) or NONE (unknown format: show a generic
 * description plus {@code summary} as technical detail)
 */
public record AuditEventResponse(long id, Instant occurredAt, Long actorId, String actorName, String action,
		String entityType, String entityId, Long branchId, UUID operationId, String correlationId, String summary,
		Map<String, Object> details, String detailsSource) {

	static AuditEventResponse from(AuditEvent event) {
		Map<String, Object> details = event.getDetails();
		String source = "RECORDED";
		if (details == null || details.isEmpty()) {
			details = LegacyAuditSummaries.parse(event.getAction(), event.getSummary());
			source = details.isEmpty() && !isParameterless(event.getAction()) ? "NONE" : "LEGACY";
		}
		return new AuditEventResponse(event.getId(), event.getOccurredAt(), event.getActorId(), event.getActorName(),
				event.getAction(), event.getEntityType(), event.getEntityId(), event.getBranchId(),
				event.getOperationId(), event.getCorrelationId(), event.getSummary(), details, source);
	}

	/** Actions whose description needs no data (a legacy match yields an empty map legitimately). */
	private static boolean isParameterless(String action) {
		return AuditAction.USER_PASSWORD_RESET.name().equals(action);
	}

}
