package dev.jeffrojas.electronicarojas.notifications;

import java.util.Map;

import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.EventType;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SubjectType;

/**
 * What a business use case asks to notify, inside its own transaction.
 *
 * @param sourceEventKey stable id of the business event (e.g. "rsh-42", a status-history row), so
 * enqueuing the same event twice never creates a second message
 * @param params template parameters only: codes, device, dates, branch. Never internal notes,
 * diagnoses or data of other customers.
 */
public record NotificationRequest(EventType eventType, String sourceEventKey, long customerId, long branchId,
		SubjectType subjectType, long subjectId, Map<String, Object> params) {
}
