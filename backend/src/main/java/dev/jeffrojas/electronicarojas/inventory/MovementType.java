package dev.jeffrojas.electronicarojas.inventory;

/**
 * Kinds of stock movement and the sign of their quantity (mirrored by a CHECK in V3). Clients
 * always send a positive quantity; the type decides whether it adds or removes units.
 */
public enum MovementType {

	/** Goods received (purchase, return to stock). */
	RECEIPT(1, true),
	/** Goods leaving stock outside a transfer (sale, loss, internal use). */
	ISSUE(-1, true),
	/** Physical count found more units than recorded. */
	ADJUSTMENT_IN(1, true),
	/** Physical count found fewer units than recorded. */
	ADJUSTMENT_OUT(-1, true),
	TRANSFER_OUT(-1, false),
	TRANSFER_IN(1, false),
	/** Spare part used in a repair order (BR-REP-007); only created by {@link RepairStock}. */
	OUT_FOR_REPAIR(-1, false),
	/** Correction of a repair consumption: the unit goes back to the shelf (BR-REP-012). */
	RETURN_FROM_REPAIR(1, false);

	private final int sign;

	private final boolean standalone;

	MovementType(int sign, boolean standalone) {
		this.sign = sign;
		this.standalone = standalone;
	}

	/**
	 * Receipts, issues and adjustments; transfer legs are only created by a transfer and repair
	 * movements only by a repair order.
	 */
	public boolean isStandalone() {
		return standalone;
	}

	int signedDelta(int quantity) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("Quantity must be strictly positive");
		}
		return sign * quantity;
	}

}
