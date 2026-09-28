package dev.jeffrojas.electronicarojas.inventory;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.MovementResponse;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.OverviewCell;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.OverviewRow;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.ProductSummary;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.StockOverviewResponse;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.StockItemResponse;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;

/**
 * Stock and movement history of one branch. Every method first resolves the branch through
 * {@link BranchService}, the single branch-scope check: a foreign or missing branch is a 404.
 */
@Service
@Transactional(readOnly = true)
public class BranchStockService {

	private final BranchStockRepository stocks;

	private final StockMovementRepository movements;

	private final BranchService branchService;

	private final ProductCatalogService catalog;

	private final StockLedger ledger;

	private final AuditService audit;

	private final Clock clock;

	BranchStockService(BranchStockRepository stocks, StockMovementRepository movements, BranchService branchService,
			ProductCatalogService catalog, StockLedger ledger, AuditService audit, Clock clock) {
		this.stocks = stocks;
		this.movements = movements;
		this.branchService = branchService;
		this.catalog = catalog;
		this.ledger = ledger;
		this.audit = audit;
		this.clock = clock;
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public PageResponse<StockItemResponse> list(CurrentUser user, long branchId, String search, String category,
			ProductKind kind, Boolean active, StockStatusFilter stockStatus, int page, int size) {
		Branch branch = branchService.requireReadable(user, branchId);
		return PageResponse.of(stocks.searchBranchStock(branch.getId(), SearchPattern.contains(search),
				blankToNull(category), kind, active, stockStatus == null ? null : stockStatus.name(),
				PageRequest.of(page, size)), StockItemResponse::from);
	}

	/**
	 * Consolidated stock across every ACTIVE branch the user may read (Phase 3, A.3). The branch
	 * scope is resolved first and passed into SQL, so no foreign stock is ever read; then one query
	 * pages the products and one query loads only that page's cells (no N+1, no unbounded list).
	 */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public StockOverviewResponse overview(CurrentUser user, String search, String category, ProductKind kind,
			Boolean active, StockStatusFilter stockStatus, int page, int size) {
		List<BranchSummary> branches = branchService.listSelectable(user);
		if (branches.isEmpty()) {
			return new StockOverviewResponse(List.of(), new PageResponse<>(List.of(), page, size, 0, 0));
		}
		List<Long> branchIds = branches.stream().map(BranchSummary::id).toList();
		Page<Product> products = stocks.searchOverview(branchIds, branchIds.size(), SearchPattern.contains(search),
				blankToNull(category), kind, active, stockStatus == null ? null : stockStatus.name(),
				PageRequest.of(page, size));
		List<Long> productIds = products.map(Product::getId).getContent();
		Map<Long, Map<Long, StockCellView>> cellsByProduct = productIds.isEmpty() ? Map.of()
				: stocks.findCells(productIds, branchIds)
					.stream()
					.collect(Collectors.groupingBy(StockCellView::productId,
							Collectors.toMap(StockCellView::branchId, Function.identity())));
		return new StockOverviewResponse(branches, PageResponse.of(products,
				product -> overviewRow(product, branchIds, cellsByProduct.getOrDefault(product.getId(), Map.of()))));
	}

	private static OverviewRow overviewRow(Product product, List<Long> branchIds, Map<Long, StockCellView> cells) {
		List<OverviewCell> row = branchIds.stream().map(branchId -> {
			StockCellView cell = cells.get(branchId);
			int quantity = cell == null ? 0 : cell.quantity();
			int minimum = cell == null ? 0 : cell.minimumQuantity();
			return new OverviewCell(branchId, quantity, minimum, StockStatus.of(quantity, minimum));
		}).toList();
		int total = row.stream().mapToInt(OverviewCell::quantity).sum();
		int out = (int) row.stream().filter(cell -> cell.stockStatus() == StockStatus.OUT_OF_STOCK).count();
		int low = (int) row.stream().filter(cell -> cell.stockStatus() == StockStatus.LOW).count();
		return new OverviewRow(ProductSummary.from(product), total, out, low, row);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public PageResponse<MovementResponse> history(CurrentUser user, long branchId, Long productId, MovementType type,
			int page, int size) {
		Branch branch = branchService.requireReadable(user, branchId);
		return PageResponse.of(movements.findHistory(branch.getId(), productId, type, PageRequest.of(page, size)),
				MovementResponse::from);
	}

	/** BR-INV-005: each branch sets its own minimum; the low-stock alert is derived from it. */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
	@Transactional
	public StockItemResponse changeMinimum(CurrentUser user, long branchId, long productId, int minimumQuantity) {
		Branch branch = branchService.requireOperable(user, branchId);
		Product product = catalog.require(productId);
		BranchStock stock = ledger.lock(product, branch).get(branch.getId());
		int previous = stock.getMinimumQuantity();
		stock.changeMinimum(minimumQuantity, clock.instant());
		stocks.saveAndFlush(stock);
		audit.record(user, AuditEntry.of(AuditAction.STOCK_MINIMUM_CHANGED, stock.getId())
			.branch(branch.getId())
			.detail("sku", product.getSku())
			.detail("productName", product.getName())
			.detail("branchCode", branch.getCode())
			.detail("branchName", branch.getName())
			.detail("previousMinimum", previous)
			.detail("newMinimum", minimumQuantity)
			.summary("Minimum of " + product.getSku() + " at " + branch.getCode() + " changed from " + previous
					+ " to " + minimumQuantity));
		return StockItemResponse.from(stock);
	}

}
