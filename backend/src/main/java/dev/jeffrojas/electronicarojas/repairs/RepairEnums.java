package dev.jeffrojas.electronicarojas.repairs;

/** Small value enums of the repairs module. */
public final class RepairEnums {

	private RepairEnums() {
	}

	/** How the work ended; kept after delivery so "delivered" still tells repaired or not. */
	public enum Resolution {
		REPAIRED, CANCELLED, UNREPAIRABLE
	}

	public enum QuoteStatus {
		PENDING, APPROVED, REJECTED
	}

	/** How the customer communicated the decision on a quote. */
	public enum DecisionMethod {
		IN_PERSON, PHONE, EMAIL, MESSAGE
	}

	/** The two explicit decisions; there is no default. */
	public enum QuoteDecision {
		APPROVED, REJECTED
	}

}
