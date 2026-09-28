package dev.jeffrojas.electronicarojas.inventory;

import java.time.Instant;

/**
 * JPQL projection of one catalog product with its stock at a given branch. Boxed types because
 * the values come from a LEFT JOIN (no stock row yet means quantity 0 and no update date).
 */
record StockItemView(Long productId, String sku, String name, String category, ProductKind kind, Boolean active,
		Integer quantity, Integer minimumQuantity, Instant updatedAt) {
}
