package dev.jeffrojas.electronicarojas.servicerequests;

import static dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus.ACCEPTED;
import static dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus.CANCELLED;
import static dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus.PENDING;
import static dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus.REJECTED;
import static dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus.UNDER_REVIEW;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.security.Role;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus;

/**
 * Transition tables and role rules of home service (ER-BR-001 section 8, v1.3). Pure Java;
 * the services enforce it and the API returns its answers as the actions the UI may offer.
 */
final class ServicePolicy {

	private static final Map<RequestStatus, Set<RequestStatus>> REQUEST = new EnumMap<>(RequestStatus.class);

	private static final Map<VisitStatus, Set<VisitStatus>> VISIT = new EnumMap<>(VisitStatus.class);

	static {
		REQUEST.put(PENDING, EnumSet.of(UNDER_REVIEW, REJECTED, CANCELLED));
		REQUEST.put(UNDER_REVIEW, EnumSet.of(ACCEPTED, REJECTED, CANCELLED));
		// A confirmed visit cancelled before it happens sends the request back to review (new time).
		REQUEST.put(ACCEPTED, EnumSet.of(UNDER_REVIEW, CANCELLED));
		REQUEST.put(REJECTED, EnumSet.noneOf(RequestStatus.class));
		REQUEST.put(CANCELLED, EnumSet.noneOf(RequestStatus.class));

		VISIT.put(VisitStatus.PROPOSED, EnumSet.of(VisitStatus.CONFIRMED, VisitStatus.CANCELLED));
		VISIT.put(VisitStatus.CONFIRMED, EnumSet.of(VisitStatus.IN_PROGRESS, VisitStatus.CANCELLED));
		VISIT.put(VisitStatus.IN_PROGRESS, EnumSet.of(VisitStatus.COMPLETED));
		VISIT.put(VisitStatus.COMPLETED, EnumSet.noneOf(VisitStatus.class));
		VISIT.put(VisitStatus.CANCELLED, EnumSet.noneOf(VisitStatus.class));
	}

	private ServicePolicy() {
	}

	static boolean canMove(RequestStatus from, RequestStatus to) {
		return REQUEST.get(from).contains(to);
	}

	static boolean canMove(VisitStatus from, VisitStatus to) {
		return VISIT.get(from).contains(to);
	}

	/** Open requests accept customer links, branch changes and new visits. */
	static boolean isOpen(RequestStatus status) {
		return status == PENDING || status == UNDER_REVIEW || status == ACCEPTED;
	}

	/** A visit can be moved (new time or technician) until it starts. */
	static boolean canReschedule(VisitStatus status) {
		return status == VisitStatus.PROPOSED || status == VisitStatus.CONFIRMED;
	}

	/** Counter work: reviewing, linking customers, scheduling, rejecting, cancelling. */
	static boolean isStaff(CurrentUser user) {
		return user.role() == Role.ADMIN || user.role() == Role.BRANCH_MANAGER || user.role() == Role.RECEPTIONIST;
	}

	/** Moving a request to another branch changes who can see it: management only. */
	static boolean mayChangeBranch(CurrentUser user) {
		return user.role() == Role.ADMIN || user.role() == Role.BRANCH_MANAGER;
	}

	/** Starting and finishing a visit: its technician, or management covering for them. */
	static boolean mayWorkVisit(CurrentUser user, ServiceVisit visit) {
		if (user.role() == Role.ADMIN || user.role() == Role.BRANCH_MANAGER) {
			return true;
		}
		return user.role() == Role.TECHNICIAN && visit.getTechnician().getId() == user.id();
	}

	/**
	 * Technicians see the address and phone of their own visits only while the visit is ahead of
	 * them or running (they need them to get there); afterwards, names only (data minimization).
	 */
	static boolean mayViewContact(CurrentUser user, ServiceVisit visit) {
		if (user.role() != Role.TECHNICIAN) {
			return true;
		}
		return visit != null && visit.getTechnician().getId() == user.id()
				&& (visit.getStatus() == VisitStatus.CONFIRMED || visit.getStatus() == VisitStatus.IN_PROGRESS);
	}

}
