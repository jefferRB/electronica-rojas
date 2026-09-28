package dev.jeffrojas.electronicarojas.servicerequests;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.notifications.NotificationEnums;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SubjectType;
import dev.jeffrojas.electronicarojas.notifications.NotificationOutbox;
import dev.jeffrojas.electronicarojas.notifications.NotificationRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.EventType;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus;

/**
 * C.2 for home visits: which timeline events the customer is told about. Called by
 * {@link ServiceTimeline}, the single place where visit events are recorded, so every path that
 * confirms, moves or cancels a visit (including cancelling the whole request) notifies the same way.
 * <ul>
 * <li>Confirmed: always.</li>
 * <li>Rescheduled / cancelled: only when the visit was CONFIRMED. A proposed visit was never
 * promised to the customer, so changing it is not news for them.</li>
 * </ul>
 */
@Component
class VisitNotifications {

	private final NotificationOutbox outbox;

	VisitNotifications(NotificationOutbox outbox) {
		this.outbox = outbox;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	void onTimelineEvent(long eventId, ServiceRequest request, ServiceVisit visit, EventType type, Enum<?> from,
			Map<String, Object> details) {
		if (visit == null || request.getCustomer() == null) {
			return;
		}
		NotificationEnums.EventType notification = switch (type) {
			case VISIT_CONFIRMED -> NotificationEnums.EventType.VISIT_CONFIRMED;
			case VISIT_RESCHEDULED -> visit.getStatus() == VisitStatus.CONFIRMED
					? NotificationEnums.EventType.VISIT_RESCHEDULED : null;
			case VISIT_CANCELLED -> from == VisitStatus.CONFIRMED ? NotificationEnums.EventType.VISIT_CANCELLED : null;
			default -> null;
		};
		if (notification == null) {
			return;
		}
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("requestCode", request.getRequestCode());
		params.put("publicRef", request.getPublicRef().toString());
		params.put("deviceType", request.getDeviceType());
		if (request.getBrand() != null) {
			params.put("brand", request.getBrand());
		}
		params.put("branchName", request.getBranch().getName());
		params.put("start", visit.getScheduledStart().toString());
		params.put("end", visit.getScheduledEnd().toString());
		if (details != null && details.get("previousStart") != null) {
			params.put("previousStart", details.get("previousStart"));
			params.put("previousEnd", details.get("previousEnd"));
		}
		outbox.enqueue(new NotificationRequest(notification, "sre-" + eventId, request.getCustomer().getId(),
				request.getBranch().getId(), SubjectType.SERVICE_VISIT, visit.getId(), params));
	}

}
