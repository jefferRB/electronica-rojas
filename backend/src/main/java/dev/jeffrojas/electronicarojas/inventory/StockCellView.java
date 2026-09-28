package dev.jeffrojas.electronicarojas.inventory;

/** JPQL projection: the balance of one product at one branch (consolidated view). */
record StockCellView(Long productId, Long branchId, Integer quantity, Integer minimumQuantity) {
}
