package dev.jeffrojas.electronicarojas.repairs;

import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.APPROVED;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.AWAITING_APPROVAL;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.CANCELLED;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.DELIVERED;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.DIAGNOSING;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.IN_REPAIR;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.READY_FOR_PICKUP;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.RECEIVED;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.UNREPAIRABLE;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.jeffrojas.electronicarojas.repairs.RepairEnums.Resolution;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.security.Role;

/**
 * The single source of the repair workflow rules (ENG-015): which transitions exist, which of them
 * are driven only by quotes, which need a reason or a technician, and which roles may perform
 * each action. Pure Java (no Spring, no database) so it is unit-tested exhaustively; the services
 * enforce it and the API returns its answers to the UI as the list of available actions.
 */
final class RepairPolicy {

	private static final Map<RepairStatus, Set<RepairStatus>> TRANSITIONS = new EnumMap<>(RepairStatus.class);

	static {
		TRANSITIONS.put(RECEIVED, EnumSet.of(DIAGNOSING, CANCELLED));
		TRANSITIONS.put(DIAGNOSING, EnumSet.of(AWAITING_APPROVAL, IN_REPAIR, UNREPAIRABLE, CANCELLED));
		TRANSITIONS.put(AWAITING_APPROVAL, EnumSet.of(APPROVED, CANCELLED));
		TRANSITIONS.put(APPROVED, EnumSet.of(IN_REPAIR, CANCELLED));
		TRANSITIONS.put(IN_REPAIR, EnumSet.of(READY_FOR_PICKUP, AWAITING_APPROVAL, UNREPAIRABLE));
		TRANSITIONS.put(READY_FOR_PICKUP, EnumSet.of(DELIVERED));
		// v1.2: the physical return of an unrepaired appliance is the explicit DELIVERED act.
		TRANSITIONS.put(CANCELLED, EnumSet.of(DELIVERED));
		TRANSITIONS.put(UNREPAIRABLE, EnumSet.of(DELIVERED));
		TRANSITIONS.put(DELIVERED, EnumSet.noneOf(RepairStatus.class));
	}

	/** Work done by the workshop: admin, branch manager, or the technician assigned to the order. */
	private static final Set<RepairStatus> TECHNICAL_TARGETS = EnumSet.of(DIAGNOSING, IN_REPAIR, UNREPAIRABLE,
			READY_FOR_PICKUP);

	/** Customer-facing acts: admin, branch manager or receptionist. */
	private static final Set<RepairStatus> COUNTER_TARGETS = EnumSet.of(CANCELLED, DELIVERED);

	private static final Set<RepairStatus> REASON_REQUIRED = EnumSet.of(CANCELLED, UNREPAIRABLE);

	private static final Set<RepairStatus> TECHNICIAN_REQUIRED = EnumSet.of(DIAGNOSING, IN_REPAIR);

	/** Statuses where the technical record (diagnosis, technician) can still change. */
	private static final Set<RepairStatus> OPEN_WORK = EnumSet.of(RECEIVED, DIAGNOSING, AWAITING_APPROVAL, APPROVED,
			IN_REPAIR);

	private RepairPolicy() {
	}

	static boolean exists(RepairStatus from, RepairStatus to) {
		return TRANSITIONS.get(from).contains(to);
	}

	/**
	 * Transitions that only happen through a quote: AWAITING_APPROVAL (creating a quote), APPROVED
	 * (approving it) and AWAITING_APPROVAL -> CANCELLED (rejecting it). They cannot be requested
	 * through the generic status endpoint.
	 */
	static boolean isQuoteDriven(RepairStatus from, RepairStatus to) {
		return to == AWAITING_APPROVAL || to == APPROVED || (from == AWAITING_APPROVAL && to == CANCELLED);
	}

	static boolean requiresReason(RepairStatus to) {
		return REASON_REQUIRED.contains(to);
	}

	static boolean requiresTechnician(RepairStatus to) {
		return TECHNICIAN_REQUIRED.contains(to);
	}

	static Resolution resolutionFor(RepairStatus to) {
		return switch (to) {
			case READY_FOR_PICKUP -> Resolution.REPAIRED;
			case CANCELLED -> Resolution.CANCELLED;
			case UNREPAIRABLE -> Resolution.UNREPAIRABLE;
			default -> null;
		};
	}

	/** May this user request {@code to} for this order through the status endpoint? */
	static boolean mayRequest(CurrentUser user, RepairOrder order, RepairStatus to) {
		RepairStatus from = order.getStatus();
		if (!exists(from, to) || isQuoteDriven(from, to)) {
			return false;
		}
		return TECHNICAL_TARGETS.contains(to) ? isTechnicalActor(user, order) : isCounterActor(user);
	}

	/** Manual transitions the UI may offer this user now. */
	static List<RepairStatus> availableTransitions(CurrentUser user, RepairOrder order) {
		return TRANSITIONS.get(order.getStatus()).stream().filter(to -> mayRequest(user, order, to)).toList();
	}

	static boolean mayAssignTechnician(CurrentUser user, RepairOrder order) {
		return isManagement(user) && OPEN_WORK.contains(order.getStatus());
	}

	static boolean mayEditDiagnosis(CurrentUser user, RepairOrder order) {
		return isTechnicalActor(user, order) && OPEN_WORK.contains(order.getStatus()) && order.getStatus() != RECEIVED;
	}

	/** Statuses from which a quote can be issued (DIAGNOSING, or a budget change during IN_REPAIR). */
	static boolean mayCreateQuote(CurrentUser user, RepairOrder order) {
		return isTechnicalActor(user, order) && (order.getStatus() == DIAGNOSING || order.getStatus() == IN_REPAIR);
	}

	/** Recording the customer's decision is a counter act, never done by the technician. */
	static boolean mayDecideQuote(CurrentUser user, RepairOrder order) {
		return isCounterActor(user) && order.getStatus() == AWAITING_APPROVAL;
	}

	/**
	 * BR-REP-011: spare parts are consumed while the repair is being done (IN_REPAIR), by the
	 * workshop (admin, branch manager or the assigned technician). A technician gets no general
	 * inventory access from this: only the spare parts of the order's branch, through the order.
	 */
	static boolean mayConsumeParts(CurrentUser user, RepairOrder order) {
		return isTechnicalActor(user, order) && order.getStatus() == IN_REPAIR;
	}

	/** Who may consume parts on this order at all (the status is checked separately, as a 409). */
	static boolean isPartsActor(CurrentUser user, RepairOrder order) {
		return isTechnicalActor(user, order);
	}

	/**
	 * BR-REP-012: correcting a consumption. While the work is open, the workshop; once the order is
	 * closed (ready, delivered, cancelled, unrepairable) only management, and the correction is
	 * audited as such. The status history is never touched.
	 */
	static boolean mayReturnParts(CurrentUser user, RepairOrder order) {
		return isManagement(user) || (isTechnicalActor(user, order) && OPEN_WORK.contains(order.getStatus()));
	}

	/**
	 * BR-REP-014: setting a part line's price or charging decision away from the catalog defaults
	 * (warranty, courtesy, workshop mistake, promotion) is a commercial decision of management. The
	 * technician records parts with the catalog defaults.
	 */
	static boolean mayOverridePartPricing(CurrentUser user) {
		return isManagement(user);
	}

	static boolean isClosed(RepairOrder order) {
		return !OPEN_WORK.contains(order.getStatus());
	}

	/** Technicians see only names inside orders, never the customer's contact data. */
	static boolean mayViewCustomerContact(CurrentUser user) {
		return user.role() != Role.TECHNICIAN;
	}

	private static boolean isManagement(CurrentUser user) {
		return user.role() == Role.ADMIN || user.role() == Role.BRANCH_MANAGER;
	}

	private static boolean isCounterActor(CurrentUser user) {
		return isManagement(user) || user.role() == Role.RECEPTIONIST;
	}

	private static boolean isTechnicalActor(CurrentUser user, RepairOrder order) {
		if (isManagement(user)) {
			return true;
		}
		return user.role() == Role.TECHNICIAN && order.getAssignedTechnician() != null
				&& order.getAssignedTechnician().getId() == user.id();
	}

}
