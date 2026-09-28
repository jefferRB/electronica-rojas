package dev.jeffrojas.electronicarojas.servicerequests;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.security.Role;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;

/**
 * Resource authorization of home service (ARCH-SEC-005): ADMIN everything; BRANCH_MANAGER and
 * RECEPTIONIST requests and visits of their active branches; TECHNICIAN only the visits assigned
 * to them (never the request inbox). Anything else is the same 404 as a missing record.
 */
@Component
class ServiceRequestAccess {

	private static final String REQUEST_NOT_FOUND = "Service request not found.";

	private static final String VISIT_NOT_FOUND = "Visit not found.";

	private final ServiceRequestRepository requests;

	private final ServiceVisitRepository visits;

	private final BranchService branchService;

	ServiceRequestAccess(ServiceRequestRepository requests, ServiceVisitRepository visits, BranchService branchService) {
		this.requests = requests;
		this.visits = visits;
		this.branchService = branchService;
	}

	ServiceRequest requireRequest(CurrentUser user, long requestId) {
		ServiceRequest request = requests.findById(requestId).orElseThrow(() -> new NotFoundException(REQUEST_NOT_FOUND));
		if (!canSeeRequest(user, request)) {
			throw new NotFoundException(REQUEST_NOT_FOUND);
		}
		return request;
	}

	/** Row lock (SELECT ... FOR UPDATE) before the check: every decision on a request is serialized. */
	@Transactional(propagation = Propagation.MANDATORY)
	ServiceRequest requireRequestForUpdate(CurrentUser user, long requestId) {
		ServiceRequest request = requests.lockById(requestId).orElseThrow(() -> new NotFoundException(REQUEST_NOT_FOUND));
		if (!canSeeRequest(user, request)) {
			throw new NotFoundException(REQUEST_NOT_FOUND);
		}
		return request;
	}

	ServiceVisit requireVisit(CurrentUser user, long visitId) {
		ServiceVisit visit = visits.findById(visitId).orElseThrow(() -> new NotFoundException(VISIT_NOT_FOUND));
		if (!canSeeVisit(user, visit)) {
			throw new NotFoundException(VISIT_NOT_FOUND);
		}
		return visit;
	}

	/** Locks the visit and its request (always in this order: request first, then visit). */
	@Transactional(propagation = Propagation.MANDATORY)
	ServiceVisit requireVisitForUpdate(CurrentUser user, long visitId) {
		ServiceVisit unlocked = visits.findById(visitId).orElseThrow(() -> new NotFoundException(VISIT_NOT_FOUND));
		if (!canSeeVisit(user, unlocked)) {
			throw new NotFoundException(VISIT_NOT_FOUND);
		}
		requests.lockById(unlocked.getRequest().getId());
		return visits.lockById(visitId).orElseThrow(() -> new NotFoundException(VISIT_NOT_FOUND));
	}

	boolean canSeeRequest(CurrentUser user, ServiceRequest request) {
		if (user.isAdmin()) {
			return true;
		}
		return ServicePolicy.isStaff(user) && readable(user, request.getBranch().getId());
	}

	boolean canSeeVisit(CurrentUser user, ServiceVisit visit) {
		if (user.role() == Role.TECHNICIAN) {
			return visit.getTechnician().getId() == user.id() && readable(user, visit.getRequest().getBranch().getId());
		}
		return canSeeRequest(user, visit.getRequest());
	}

	private boolean readable(CurrentUser user, long branchId) {
		return branchService.isReadable(user, branchId);
	}

}
