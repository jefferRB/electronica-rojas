package dev.jeffrojas.electronicarojas.inventory;

/**
 * Derived state of a (branch, product) balance (BR-INV-005, clarified in ER-BR-001 1.2). Computed
 * only here so every view (branch stock, consolidated view, product detail) shows the same thing.
 * It never changes balances or transactional rules.
 */
public enum StockStatus {

	/** No units at the branch, whatever the minimum. Shown even when no minimum is set. */
	OUT_OF_STOCK,
	/** Some units, but fewer than the configured minimum (minimum > 0). Needs replenishment. */
	LOW,
	/** Neither of the above. */
	NORMAL;

	public static StockStatus of(int quantity, int minimumQuantity) {
		if (quantity == 0) {
			return OUT_OF_STOCK;
		}
		if (minimumQuantity > 0 && quantity < minimumQuantity) {
			return LOW;
		}
		return NORMAL;
	}

}
