package dev.jeffrojas.electronicarojas.repairs;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.EventType;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SubjectType;
import dev.jeffrojas.electronicarojas.notifications.NotificationOutbox;
import dev.jeffrojas.electronicarojas.notifications.NotificationRequest;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * The only code that changes a repair order's status (ENG-015). Validates the transition against
 * {@link RepairPolicy}, then writes the new status, the immutable history entry and the audit event
 * in the caller's transaction, on an order row the caller has already locked. If anything fails,
 * nothing of it is persisted.
 */
@Component
class RepairWorkflow {

	private final RepairStatusChangeRepository history;

	private final NotificationOutbox notifications;

	private final AuditService audit;

	private final EntityManager entityManager;

	private final Clock clock;

	RepairWorkflow(RepairStatusChangeRepository history, NotificationOutbox notifications, AuditService audit,
			EntityManager entityManager, Clock clock) {
		this.history = history;
		this.notifications = notifications;
		this.audit = audit;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	/** First timeline entry, written together with the new order. */
	@Transactional(propagation = Propagation.MANDATORY)
	void recordReception(CurrentUser user, RepairOrder order) {
		history.save(new RepairStatusChange(order, null, RepairStatus.RECEIVED, null, actor(user), order.getReceivedAt()));
	}

	/**
	 * @param viaQuote true when a quote action drives the change (the only way to reach
	 * AWAITING_APPROVAL and APPROVED, or to cancel from AWAITING_APPROVAL); the quote service has
	 * already checked its own permissions then.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	void change(CurrentUser user, RepairOrder order, RepairStatus target, String reason, boolean viaQuote) {
		RepairStatus from = order.getStatus();
		if (!RepairPolicy.exists(from, target) || (!viaQuote && RepairPolicy.isQuoteDriven(from, target))) {
			throw new ConflictException("INVALID_TRANSITION", "Transition " + from + " -> " + target + " is not allowed.",
					Map.of("fromStatus", from.name(), "toStatus", target.name()));
		}
		if (!viaQuote && !RepairPolicy.mayRequest(user, order, target)) {
			throw new AccessDeniedException("Role not allowed to move the order to " + target);
		}
		String cleanReason = reason == null || reason.isBlank() ? null : reason.strip();
		if (RepairPolicy.requiresReason(target) && cleanReason == null) {
			throw new InvalidRequestException("reason", "REASON_REQUIRED", "a reason is required for " + target);
		}
		if (RepairPolicy.requiresTechnician(target) && order.getAssignedTechnician() == null) {
			throw new ConflictException("TECHNICIAN_REQUIRED", "Assign a technician before moving to " + target + ".");
		}

		Instant now = clock.instant();
		AppUser actor = actor(user);
		order.moveTo(target, RepairPolicy.resolutionFor(target), actor, now);
		RepairStatusChange change = history.save(new RepairStatusChange(order, from, target, cleanReason, actor, now));
		if (target == RepairStatus.READY_FOR_PICKUP) {
			notifyReady(order, change);
		}
		// Reason text stays in the order's own timeline; the audit keeps only codes (no free text that
		// might contain customer data).
		audit.record(user, AuditEntry.of(AuditAction.REPAIR_STATUS_CHANGED, order.getId())
			.branch(order.getBranch().getId())
			.detail("orderCode", order.getOrderCode())
			.detail("fromStatus", from)
			.detail("toStatus", target)
			.detail("branchCode", order.getBranch().getCode())
			.detail("branchName", order.getBranch().getName())
			.summary("Order " + order.getOrderCode() + " " + from + " -> " + target));
	}

	/**
	 * C.2: "your appliance is ready". Stored in the outbox inside this transaction, delivered after
	 * commit (BR-REP-006: failing to e-mail never changes the status). Keyed by the history row, so
	 * the same event is never enqueued twice.
	 */
	private void notifyReady(RepairOrder order, RepairStatusChange change) {
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("orderCode", order.getOrderCode());
		params.put("deviceType", order.getDeviceType());
		params.put("brand", order.getBrand());
		params.put("branchName", order.getBranch().getName());
		if (order.getBranch().getAddress() != null) {
			params.put("branchAddress", order.getBranch().getAddress());
		}
		notifications.enqueue(new NotificationRequest(EventType.REPAIR_READY_FOR_PICKUP, "rsh-" + change.getId(),
				order.getCustomer().getId(), order.getBranch().getId(), SubjectType.REPAIR_ORDER, order.getId(), params));
	}

	private AppUser actor(CurrentUser user) {
		return entityManager.getReference(AppUser.class, user.id());
	}

}
