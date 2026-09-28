package dev.jeffrojas.electronicarojas.inventory;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.ProductSummary;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.SparePartOption;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * Inventory side of spare-part consumption (BR-REP-007): the ONLY way the repairs module changes
 * stock. Every change goes through {@link StockLedger} (row lock in (branch, product) order) and
 * leaves an immutable {@link StockMovement} linked to the repair order.
 * <p>
 * Authorization is the caller's job: the repairs use case has already decided that this user may
 * work on this order and operate this branch. Writes join the caller's transaction
 * ({@code MANDATORY}), so the movement and the repair's own records commit or roll back together.
 */
@Component
public class RepairStock {

	private final StockLedger ledger;

	private final StockMovementRepository movements;

	private final StockTransferRepository transfers;

	private final BranchStockRepository stocks;

	private final ProductCatalogService catalog;

	private final ProductRepository products;

	private final EntityManager entityManager;

	private final Clock clock;

	RepairStock(StockLedger ledger, StockMovementRepository movements, StockTransferRepository transfers,
			BranchStockRepository stocks, ProductCatalogService catalog, ProductRepository products,
			EntityManager entityManager, Clock clock) {
		this.ledger = ledger;
		this.movements = movements;
		this.transfers = transfers;
		this.stocks = stocks;
		this.catalog = catalog;
		this.products = products;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	/** What a repair movement did to the shelf, for the repair's own records and audit. */
	public record StockChange(long movementId, Product product, int balanceBefore, int balanceAfter) {
	}

	/**
	 * Active spare parts with their stock at one branch: the same query and the same stock status
	 * as the branch stock screen, restricted to {@code SPARE_PART}, so both show the same numbers.
	 * Each option also carries the defaults a repair line takes: sale price and chargeability (never
	 * the cost). Prices come from one extra query for the page, not one per row.
	 */
	@Transactional(readOnly = true)
	public PageResponse<SparePartOption> spareParts(long branchId, String search, int page, int size) {
		var views = stocks.searchBranchStock(branchId, SearchPattern.contains(search), null, ProductKind.SPARE_PART,
				true, null, PageRequest.of(page, size));
		Map<Long, Product> byId = products.findAllById(views.map(StockItemView::productId).toList())
			.stream()
			.collect(Collectors.toMap(Product::getId, Function.identity()));
		return PageResponse.of(views, view -> {
			Product product = byId.get(view.productId());
			int quantity = view.quantity() == null ? 0 : view.quantity();
			int minimum = view.minimumQuantity() == null ? 0 : view.minimumQuantity();
			return new SparePartOption(ProductSummary.from(product), quantity, minimum, StockStatus.of(quantity, minimum),
					view.updatedAt(), product.getSalePrice(), product.isChargeableByDefault());
		});
	}

	/**
	 * The product a repair may use: an active spare part (404 missing, 409 inactive, 400 other kind).
	 * Read before any stock is touched, so the caller can decide the line's price with it.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Product requireSparePart(long productId) {
		Product product = catalog.requireActive(productId);
		if (product.getKind() != ProductKind.SPARE_PART) {
			throw new InvalidRequestException("productId", "NOT_A_SPARE_PART", "only spare parts can be used in a repair");
		}
		return product;
	}

	/**
	 * Takes {@code quantity} units of a spare part (from {@link #requireSparePart}) from the branch
	 * (409 if not enough).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public StockChange consume(long actorId, Branch branch, Product product, int quantity, long repairOrderId,
			UUID operationId, String note, String fingerprint) {
		return apply(actorId, branch, product, MovementType.OUT_FOR_REPAIR, quantity, repairOrderId, operationId, note,
				fingerprint);
	}

	/**
	 * Puts back units of a consumed part (correction). Allowed for a product deactivated since:
	 * it compensates a movement that already happened (BR-TRF-005), it is not a new sale or use.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public StockChange restock(long actorId, Branch branch, long productId, int quantity, long repairOrderId,
			UUID operationId, String reason, String fingerprint) {
		return apply(actorId, branch, catalog.require(productId), MovementType.RETURN_FROM_REPAIR, quantity,
				repairOrderId, operationId, reason, fingerprint);
	}

	private StockChange apply(long actorId, Branch branch, Product product, MovementType type, int quantity,
			long repairOrderId, UUID operationId, String reason, String fingerprint) {
		// An id already spent on a stock operation is never reused for a repair (BR-TRF-003).
		if (movements.findByOperationIdAndTransferIsNull(operationId).isPresent()
				|| transfers.existsByOperationId(operationId)) {
			throw new ConflictException("OPERATION_ID_REUSED",
					"This operationId was already used for a different operation.");
		}
		BranchStock stock = ledger.lock(product, branch).get(branch.getId());
		Instant now = clock.instant();
		int delta = type.signedDelta(quantity);
		int balanceAfter = stock.apply(delta, now);
		StockMovement movement = movements.saveAndFlush(new StockMovement(operationId, type, stock, delta, balanceAfter,
				reason, fingerprint, repairOrderId, entityManager.getReference(AppUser.class, actorId), now));
		return new StockChange(movement.getId(), product, movement.getBalanceBefore(), balanceAfter);
	}

}
