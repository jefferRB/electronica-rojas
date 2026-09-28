package dev.jeffrojas.electronicarojas.servicerequests;

/** Value enums of the home-service module (ER-BR-001 section 8, v1.3). */
public final class ServiceEnums {

	private ServiceEnums() {
	}

	/** What happened to the customer's request (the decision), not when the visit is. */
	public enum RequestStatus {
		/** Received, nobody has acted yet. */
		PENDING,
		/** Staff is working on it: customer, branch, a proposed visit. */
		UNDER_REVIEW,
		/** A visit was confirmed; the request is being (or was) attended. */
		ACCEPTED,
		REJECTED,
		CANCELLED
	}

	/** Lifecycle of one scheduled visit; the only place where the visit's time lives. */
	public enum VisitStatus {
		/** Offered to the customer, not agreed yet: does not block the technician. */
		PROPOSED,
		/** Agreed: blocks the technician's time (database exclusion constraint). */
		CONFIRMED,
		IN_PROGRESS,
		COMPLETED,
		CANCELLED
	}

	public enum VisitOutcome {
		RESOLVED_ON_SITE,
		/** The appliance must go to the workshop: linked to a repair order. */
		NEEDS_WORKSHOP,
		NOT_RESOLVED
	}

	public enum RequestChannel {
		PUBLIC_FORM, STAFF
	}

	public enum PreferredWindow {
		MORNING, AFTERNOON, ANY
	}

	/** Costa Rica's seven provinces. */
	public enum Province {
		SAN_JOSE, ALAJUELA, CARTAGO, HEREDIA, GUANACASTE, PUNTARENAS, LIMON
	}

	public enum EventType {
		SUBMITTED,
		REVIEW_STARTED,
		CUSTOMER_LINKED,
		BRANCH_CHANGED,
		VISIT_PROPOSED,
		VISIT_CONFIRMED,
		VISIT_RESCHEDULED,
		VISIT_STARTED,
		VISIT_COMPLETED,
		VISIT_CANCELLED,
		REPAIR_ORDER_LINKED,
		REJECTED,
		CANCELLED
	}

}
