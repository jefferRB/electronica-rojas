package dev.jeffrojas.electronicarojas.repairs;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.security.Role;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;

/**
 * Single decision point for "may this user see this repair order" (ARCH-SEC-005):
 * ADMIN any order; BRANCH_MANAGER / RECEPTIONIST orders of their active branches; TECHNICIAN only
 * orders assigned to them in their active branches. Everything else is the same 404 as a missing
 * order, so order ids cannot be probed.
 */
@Component
class RepairOrderAccess {

	private static final String NOT_FOUND = "Repair order not found.";

	private final RepairOrderRepository orders;

	private final BranchService branchService;

	RepairOrderAccess(RepairOrderRepository orders, BranchService branchService) {
		this.orders = orders;
		this.branchService = branchService;
	}

	RepairOrder requireVisible(CurrentUser user, long orderId) {
		RepairOrder order = orders.findById(orderId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
		return checked(user, order);
	}

	/** Locks the row (SELECT ... FOR UPDATE) before checking; for every workflow change. */
	@Transactional(propagation = Propagation.MANDATORY)
	RepairOrder requireVisibleForUpdate(CurrentUser user, long orderId) {
		RepairOrder order = orders.lockById(orderId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
		return checked(user, order);
	}

	boolean canSee(CurrentUser user, RepairOrder order) {
		if (user.isAdmin()) {
			return true;
		}
		if (!branchService.isReadable(user, order.getBranch().getId())) {
			return false;
		}
		return user.role() != Role.TECHNICIAN
				|| (order.getAssignedTechnician() != null && order.getAssignedTechnician().getId() == user.id());
	}

	private RepairOrder checked(CurrentUser user, RepairOrder order) {
		if (!canSee(user, order)) {
			throw new NotFoundException(NOT_FOUND);
		}
		return order;
	}

}
