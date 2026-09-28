package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.EventType;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * Writes one timeline entry and its audit event in the caller's transaction (both or neither).
 * The timeline keeps the human reason; the audit keeps codes and instants only (no contact data).
 */
@Component
class ServiceTimeline {

	private final ServiceRequestEventRepository events;

	private final VisitNotifications visitNotifications;

	private final AuditService audit;

	private final EntityManager entityManager;

	private final Clock clock;

	ServiceTimeline(ServiceRequestEventRepository events, VisitNotifications visitNotifications, AuditService audit,
			EntityManager entityManager, Clock clock) {
		this.events = events;
		this.visitNotifications = visitNotifications;
		this.audit = audit;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	/**
	 * @param user null for the anonymous public form
	 * @param details codes and instants (e.g. previous and new start of a reschedule)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	void record(CurrentUser user, ServiceRequest request, ServiceVisit visit, EventType type, Enum<?> from, Enum<?> to,
			Map<String, Object> details, String reason) {
		AppUser actor = user == null ? null : entityManager.getReference(AppUser.class, user.id());
		ServiceRequestEvent event = events.save(
				new ServiceRequestEvent(request, visit, type, from, to, details, reason, actor, clock.instant()));
		// Customer notices (confirmed, moved, cancelled visit) go to the outbox in this same transaction.
		visitNotifications.onTimelineEvent(event.getId(), request, visit, type, from, details);

		AuditAction action = type == EventType.SUBMITTED ? AuditAction.SERVICE_REQUEST_SUBMITTED
				: visit != null ? AuditAction.SERVICE_VISIT_UPDATED : AuditAction.SERVICE_REQUEST_UPDATED;
		AuditEntry.Builder entry = AuditEntry.of(action, visit != null ? visit.getId() : request.getId())
			.branch(request.getBranch().getId())
			.detail("requestCode", request.getRequestCode())
			.detail("event", type)
			.detail("fromStatus", from)
			.detail("toStatus", to)
			.detail("branchCode", request.getBranch().getCode())
			.detail("branchName", request.getBranch().getName());
		new LinkedHashMap<>(details == null ? Map.of() : details).forEach(entry::detail);
		AuditEntry built = entry.summary("Service request " + request.getRequestCode() + ": " + type);
		if (user == null) {
			audit.recordSystem(built);
		}
		else {
			audit.record(user, built);
		}
	}

}
