package dev.jeffrojas.electronicarojas.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;
import dev.jeffrojas.electronicarojas.users.AppUser;

/** HTTP contracts of the inventory module (ENG-031: no JPA entity leaves the service layer). */
public final class InventoryDtos {

	/** Upper bound for one operation; keeps sums far from integer overflow. */
	static final int MAX_QUANTITY = 1_000_000;

	/** NUMERIC(12,2): ten integer digits and two decimals, like repair quotes. */
	public static final int MONEY_INTEGER_DIGITS = 10;

	private InventoryDtos() {
	}

	// ---- Catalog ----

	/**
	 * {@code unitCost} is null both when it is unknown and when the caller may not see costs
	 * ({@link PricingPolicy}): the value never leaves the server for those roles.
	 */
	public record ProductResponse(long id, String sku, String name, String category, String description,
			ProductKind kind, String unit, boolean active, BigDecimal salePrice, BigDecimal unitCost,
			boolean chargeableByDefault, long version) {

		static ProductResponse from(Product product, boolean showCost) {
			return new ProductResponse(product.getId(), product.getSku(), product.getName(), product.getCategory(),
					product.getDescription(), product.getKind(), product.getUnit(), product.isActive(),
					product.getSalePrice(), showCost ? product.getUnitCost() : null, product.isChargeableByDefault(),
					product.getVersion());
		}

	}

	public record ProductSummary(long id, String sku, String name, String category, ProductKind kind, boolean active) {

		static ProductSummary from(Product product) {
			return new ProductSummary(product.getId(), product.getSku(), product.getName(), product.getCategory(),
					product.getKind(), product.isActive());
		}

	}

	/**
	 * New catalog product. Money in colones with at most two decimals, never negative, null when
	 * unknown. {@code chargeableByDefault} defaults to true. {@code initialStock} optionally records
	 * the units already on the shelf of ONE branch, as a receipt in the same transaction.
	 */
	public record CreateProductRequest(
			@NotBlank @Pattern(regexp = "\\s*[A-Za-z0-9][A-Za-z0-9._-]{1,39}\\s*",
					message = "must be 2-40 letters, digits, dots, underscores or hyphens") String sku,
			@NotBlank @Size(max = 160) String name,
			@NotBlank @Size(max = 80) String category,
			@Size(max = 1000) String description,
			@NotNull ProductKind kind,
			@PositiveOrZero @Digits(integer = MONEY_INTEGER_DIGITS, fraction = 2) BigDecimal unitCost,
			@PositiveOrZero @Digits(integer = MONEY_INTEGER_DIGITS, fraction = 2) BigDecimal salePrice,
			Boolean chargeableByDefault,
			@Valid InitialStock initialStock) {
	}

	/** Units already on the shelf of one branch when the product is created; 0 records nothing. */
	public record InitialStock(
			@NotNull Long branchId,
			@NotNull @PositiveOrZero @Max(MAX_QUANTITY) Integer quantity) {
	}

	/** Full replacement: omitted money means "unknown"; omitted chargeableByDefault means true. */
	public record UpdateProductRequest(
			@NotBlank @Size(max = 160) String name,
			@NotBlank @Size(max = 80) String category,
			@Size(max = 1000) String description,
			@NotNull ProductKind kind,
			@PositiveOrZero @Digits(integer = MONEY_INTEGER_DIGITS, fraction = 2) BigDecimal unitCost,
			@PositiveOrZero @Digits(integer = MONEY_INTEGER_DIGITS, fraction = 2) BigDecimal salePrice,
			Boolean chargeableByDefault,
			@NotNull Boolean active,
			@NotNull Long version) {
	}

	/** FR-INV-002: stock of a product in each branch the user may see. */
	public record ProductDetailResponse(ProductResponse product, List<BranchStockEntry> stock) {
	}

	public record BranchStockEntry(BranchSummary branch, boolean branchActive, int quantity, int minimumQuantity,
			StockStatus stockStatus, Instant updatedAt) {
	}

	// ---- Branch stock ----

	/** FR-INV-001 row: product, current stock, minimum, derived status and last update. */
	public record StockItemResponse(ProductSummary product, int quantity, int minimumQuantity, StockStatus stockStatus,
			Instant updatedAt) {

		static StockItemResponse from(StockItemView view) {
			ProductSummary product = new ProductSummary(view.productId(), view.sku(), view.name(), view.category(),
					view.kind(), view.active());
			return new StockItemResponse(product, view.quantity(), view.minimumQuantity(),
					StockStatus.of(view.quantity(), view.minimumQuantity()), view.updatedAt());
		}

		static StockItemResponse from(BranchStock stock) {
			return new StockItemResponse(ProductSummary.from(stock.getProduct()), stock.getQuantity(),
					stock.getMinimumQuantity(), stock.status(), stock.getUpdatedAt());
		}

	}

	/**
	 * A spare part offered when recording parts in a repair: its stock at the order's branch plus the
	 * defaults the line will take (sale price and chargeability). Never the cost: technicians use it.
	 */
	public record SparePartOption(ProductSummary product, int quantity, int minimumQuantity, StockStatus stockStatus,
			Instant updatedAt, BigDecimal salePrice, boolean chargeableByDefault) {
	}

	/** One branch column of the consolidated view. */
	public record OverviewCell(long branchId, int quantity, int minimumQuantity, StockStatus stockStatus) {
	}

	/** One product row of the consolidated view: a cell per branch column, in column order. */
	public record OverviewRow(ProductSummary product, int totalQuantity, int outOfStockBranches, int lowBranches,
			List<OverviewCell> cells) {
	}

	/**
	 * Consolidated stock (Phase 3, A.3). {@code branches} are the columns: exactly the active
	 * branches the user may read, decided by the server before querying.
	 */
	public record StockOverviewResponse(List<BranchSummary> branches, PageResponse<OverviewRow> rows) {
	}

	public record UpdateMinimumRequest(@NotNull @PositiveOrZero @Max(MAX_QUANTITY) Integer minimumQuantity) {
	}

	// ---- Movements ----

	/**
	 * Receipt, issue or adjustment. {@code operationId} is generated by the client once per user
	 * intent and reused on retries, so a double submit is applied only once.
	 */
	public record RecordMovementRequest(
			@NotNull UUID operationId,
			@NotNull Long branchId,
			@NotNull Long productId,
			@NotNull MovementType type,
			@NotNull @Positive @Max(MAX_QUANTITY) Integer quantity,
			@NotBlank @Size(max = 300) String reason) {
	}

	public record ActorSummary(long id, String fullName) {

		static ActorSummary from(AppUser user) {
			return new ActorSummary(user.getId(), user.getFullName());
		}

	}

	public record MovementResponse(long id, UUID operationId, MovementType type, BranchSummary branch,
			ProductSummary product, int quantityDelta, int balanceBefore, int balanceAfter, String reason,
			ActorSummary actor, Instant createdAt, Long transferId, Long repairOrderId, String repairOrderCode) {

		static MovementResponse from(StockMovement movement) {
			return new MovementResponse(movement.getId(), movement.getOperationId(), movement.getType(),
					BranchSummary.from(movement.getBranch()), ProductSummary.from(movement.getProduct()),
					movement.getQuantityDelta(), movement.getBalanceBefore(), movement.getBalanceAfter(),
					movement.getReason(), ActorSummary.from(movement.getActor()), movement.getCreatedAt(),
					movement.getTransfer() == null ? null : movement.getTransfer().getId(), movement.getRepairOrderId(),
					movement.getRepairOrderCode());
		}

	}

	// ---- Transfers ----

	public record CreateTransferRequest(
			@NotNull UUID operationId,
			@NotNull Long sourceBranchId,
			@NotNull Long destinationBranchId,
			@NotNull Long productId,
			@NotNull @Positive @Max(MAX_QUANTITY) Integer quantity,
			@Size(max = 300) String reason) {
	}

	/** FR-TRF-004: id, branches, units, new balances, actor and both movements. */
	public record TransferResponse(long id, UUID operationId, ProductSummary product, BranchSummary source,
			BranchSummary destination, int quantity, String reason, int sourceBalanceAfter,
			int destinationBalanceAfter, ActorSummary actor, Instant createdAt, List<MovementResponse> movements) {
	}

}
