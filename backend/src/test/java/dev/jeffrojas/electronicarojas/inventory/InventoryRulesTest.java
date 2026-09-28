package dev.jeffrojas.electronicarojas.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import dev.jeffrojas.electronicarojas.shared.OperationFingerprint;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;

/** Pure unit tests of inventory rules (no Spring, no Docker). */
class InventoryRulesTest {

	private static final Instant NOW = Instant.parse("2026-09-26T15:00:00Z");

	private static BranchStock stock(int quantity, int minimum) {
		return new BranchStock(null, null, quantity, minimum, NOW);
	}

	@Test
	void applyingADeltaReturnsTheNewBalance() {
		BranchStock stock = stock(10, 0);

		assertThat(stock.apply(-4, NOW)).isEqualTo(6);
		assertThat(stock.apply(5, NOW)).isEqualTo(11);
		assertThat(stock.getQuantity()).isEqualTo(11);
	}

	@Test
	void stockNeverGoesNegativeAndStaysUnchangedOnRejection() {
		BranchStock stock = stock(2, 0);

		assertThatThrownBy(() -> stock.apply(-4, NOW)).isInstanceOf(InsufficientStockException.class)
			.hasMessageContaining("2 available, 4 requested")
			.satisfies(ex -> assertThat(((ConflictException) ex).properties()).containsEntry("available", 2)
				.containsEntry("requested", 4));
		assertThat(stock.getQuantity()).isEqualTo(2);
	}

	@Test
	void theLastUnitCanBeTakenButNotOneMore() {
		BranchStock stock = stock(1, 0);

		assertThat(stock.apply(-1, NOW)).isZero();
		assertThatThrownBy(() -> stock.apply(-1, NOW)).isInstanceOf(InsufficientStockException.class);
	}

	@Test
	void zeroChangesAndOverflowAreRejected() {
		assertThatThrownBy(() -> stock(5, 0).apply(0, NOW)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> stock(Integer.MAX_VALUE, 0).apply(1, NOW)).isInstanceOf(ConflictException.class);
	}

	@Test
	void stockStatusDistinguishesOutOfStockLowAndNormal() {
		assertThat(StockStatus.of(0, 0)).as("empty without a minimum is still out of stock").isEqualTo(StockStatus.OUT_OF_STOCK);
		assertThat(StockStatus.of(0, 5)).isEqualTo(StockStatus.OUT_OF_STOCK);
		assertThat(StockStatus.of(2, 3)).isEqualTo(StockStatus.LOW);
		assertThat(StockStatus.of(3, 3)).as("at the minimum is not below it").isEqualTo(StockStatus.NORMAL);
		assertThat(StockStatus.of(7, 0)).as("no minimum, no replenishment alert").isEqualTo(StockStatus.NORMAL);
		assertThat(stock(1, 5).status()).isEqualTo(StockStatus.LOW);
	}

	@Test
	void movementTypesCarryTheirSign() {
		assertThat(MovementType.RECEIPT.signedDelta(3)).isEqualTo(3);
		assertThat(MovementType.ADJUSTMENT_IN.signedDelta(3)).isEqualTo(3);
		assertThat(MovementType.TRANSFER_IN.signedDelta(3)).isEqualTo(3);
		assertThat(MovementType.ISSUE.signedDelta(3)).isEqualTo(-3);
		assertThat(MovementType.ADJUSTMENT_OUT.signedDelta(3)).isEqualTo(-3);
		assertThat(MovementType.TRANSFER_OUT.signedDelta(3)).isEqualTo(-3);
		assertThatThrownBy(() -> MovementType.RECEIPT.signedDelta(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MovementType.ISSUE.signedDelta(-2)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void onlyReceiptsIssuesAndAdjustmentsAreStandalone() {
		assertThat(Arrays.stream(MovementType.values()).filter(MovementType::isStandalone))
			.containsExactly(MovementType.RECEIPT, MovementType.ISSUE, MovementType.ADJUSTMENT_IN,
					MovementType.ADJUSTMENT_OUT);
	}

	@Test
	void fingerprintIsStableAndSensitiveToEveryField() {
		String base = OperationFingerprint.of("TRANSFER", 1L, 2L, 7L, 4, "reason");

		assertThat(OperationFingerprint.of("TRANSFER", 1L, 2L, 7L, 4, "reason")).isEqualTo(base).hasSize(64);
		assertThat(OperationFingerprint.of("TRANSFER", 1L, 2L, 7L, 5, "reason")).isNotEqualTo(base);
		assertThat(OperationFingerprint.of("TRANSFER", 2L, 1L, 7L, 4, "reason")).isNotEqualTo(base);
		assertThat(OperationFingerprint.of("TRANSFER", 1L, 2L, 7L, 4, null)).isNotEqualTo(base);
		assertThat(OperationFingerprint.of("a", "bc")).isNotEqualTo(OperationFingerprint.of("ab", "c"));
	}

	@Test
	void skusAreNormalized() {
		assertThat(Product.normalizeSku("  cmp-10.a ")).isEqualTo("CMP-10.A");
	}

	@Test
	void searchPatternsEscapeLikeWildcards() {
		assertThat(SearchPattern.contains(" Motor ")).isEqualTo("%motor%");
		assertThat(SearchPattern.contains("100%_x")).isEqualTo("%100\\%\\_x%");
		assertThat(SearchPattern.contains("  ")).isNull();
		assertThat(SearchPattern.contains(null)).isNull();
	}

}
