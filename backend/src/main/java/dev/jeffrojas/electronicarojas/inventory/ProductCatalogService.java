package dev.jeffrojas.electronicarojas.inventory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchResponse;
import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.BranchStockEntry;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.CreateProductRequest;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.InitialStock;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.ProductDetailResponse;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.ProductResponse;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.UpdateProductRequest;
import dev.jeffrojas.electronicarojas.inventory.Product.Pricing;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.OperationFingerprint;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * Company-wide catalog. Read by roles with inventory access (FS matrix: ADMIN, BRANCH_MANAGER,
 * RECEPTIONIST); maintained only by ADMIN because the catalog is global, not per branch. The unit
 * cost is returned only to the roles allowed by {@link PricingPolicy}.
 */
@Service
@Transactional(readOnly = true)
public class ProductCatalogService {

	static final int MAX_PAGE_SIZE = 100;

	private static final String NOT_FOUND = "Product not found.";

	/** Reason of the receipt that records the units on the shelf when a product is created. */
	static final String INITIAL_STOCK_REASON = "Existencias iniciales al crear el producto";

	private final ProductRepository products;

	private final BranchStockRepository stocks;

	private final BranchService branchService;

	private final StockLedger ledger;

	private final StockMovementRepository movements;

	private final AuditService audit;

	private final EntityManager entityManager;

	private final Clock clock;

	ProductCatalogService(ProductRepository products, BranchStockRepository stocks, BranchService branchService,
			StockLedger ledger, StockMovementRepository movements, AuditService audit, EntityManager entityManager,
			Clock clock) {
		this.products = products;
		this.stocks = stocks;
		this.branchService = branchService;
		this.ledger = ledger;
		this.movements = movements;
		this.audit = audit;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public PageResponse<ProductResponse> search(CurrentUser user, String search, String category, ProductKind kind,
			Boolean active, int page, int size) {
		boolean showCost = PricingPolicy.mayViewCost(user);
		return PageResponse.of(products.search(SearchPattern.contains(search), blankToNull(category), kind, active,
				PageRequest.of(page, size)), product -> ProductResponse.from(product, showCost));
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public List<String> categories() {
		return products.findCategories();
	}

	/** Catalog data plus stock in the branches the user may see (inactive products included). */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public ProductDetailResponse detail(CurrentUser user, long productId) {
		Product product = require(productId);
		List<BranchResponse> visible = branchService.listVisible(user);
		Map<Long, BranchStock> byBranch = stocks
			.findForProduct(productId, visible.stream().map(BranchResponse::id).toList())
			.stream()
			.collect(Collectors.toMap(stock -> stock.getBranch().getId(), Function.identity()));
		List<BranchStockEntry> entries = visible.stream().map(branch -> {
			BranchStock stock = byBranch.get(branch.id());
			int quantity = stock == null ? 0 : stock.getQuantity();
			int minimum = stock == null ? 0 : stock.getMinimumQuantity();
			return new BranchStockEntry(new BranchSummary(branch.id(), branch.code(), branch.name()), branch.active(),
					quantity, minimum, StockStatus.of(quantity, minimum), stock == null ? null : stock.getUpdatedAt());
		}).toList();
		return new ProductDetailResponse(ProductResponse.from(product, PricingPolicy.mayViewCost(user)), entries);
	}

	/**
	 * Creates the product and, when asked, records the units already on the shelf of one branch as a
	 * RECEIPT through the {@link StockLedger}, like any other receipt (BR-INV-008). One transaction:
	 * if the initial stock cannot be recorded, the product is not created either.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public ProductResponse create(CurrentUser user, CreateProductRequest request) {
		String sku = Product.normalizeSku(request.sku());
		if (products.existsBySku(sku)) {
			throw new ConflictException("DUPLICATE_SKU", "A product with SKU " + sku + " already exists.");
		}
		InitialStock initial = request.initialStock();
		Branch initialBranch = null;
		if (initial != null && initial.quantity() > 0) {
			// Checked before creating anything; the yes/no form does not mark the transaction rollback-only.
			if (!branchService.isOperable(user, initial.branchId())) {
				throw new InvalidRequestException("initialStock.branchId", "BRANCH_INVALID",
						"choose an active branch for the initial stock");
			}
			initialBranch = branchService.requireOperable(user, initial.branchId());
		}
		Pricing pricing = pricing(request.unitCost(), request.salePrice(), request.chargeableByDefault());
		Instant now = clock.instant();
		Product product = products.saveAndFlush(new Product(sku, request.name(), request.category(),
				request.description(), request.kind(), pricing, now));
		audit.record(user, AuditEntry.of(AuditAction.PRODUCT_CREATED, product.getId())
			.detail("sku", product.getSku())
			.detail("productName", product.getName())
			.detail("kind", product.getKind())
			.detail("salePrice", product.getSalePrice())
			.detail("unitCost", product.getUnitCost())
			.detail("chargeableByDefault", product.isChargeableByDefault())
			.detail("initialQuantity", initialBranch == null ? null : initial.quantity())
			.detail("branchCode", initialBranch == null ? null : initialBranch.getCode())
			.detail("branchName", initialBranch == null ? null : initialBranch.getName())
			.summary("Product " + product.getSku() + " created (" + product.getKind() + ")"));
		if (initialBranch != null) {
			recordInitialStock(user, product, initialBranch, initial.quantity(), now);
		}
		return ProductResponse.from(product, true);
	}

	/** Price changes apply to operations recorded from now on; repair lines keep their snapshot. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public ProductResponse update(CurrentUser user, long productId, UpdateProductRequest request) {
		Product product = require(productId);
		if (product.getVersion() != request.version()) {
			throw new ConflictException("STALE_VERSION", "The product was modified by someone else. Reload and try again.");
		}
		Pricing before = product.pricing();
		Pricing after = pricing(request.unitCost(), request.salePrice(), request.chargeableByDefault());
		product.update(request.name(), request.category(), request.description(), request.kind(), after,
				request.active(), clock.instant());
		products.saveAndFlush(product);
		audit.record(user, AuditEntry.of(AuditAction.PRODUCT_UPDATED, product.getId())
			.detail("sku", product.getSku())
			.detail("productName", product.getName())
			.detail("active", product.isActive())
			.summary("Product " + product.getSku() + " updated (active=" + product.isActive() + ")"));
		// Price changes get their own event: easy to find in the trail.
		if (!samePricing(before, after)) {
			audit.record(user, AuditEntry.of(AuditAction.PRODUCT_PRICING_CHANGED, product.getId())
				.detail("sku", product.getSku())
				.detail("productName", product.getName())
				.detail("salePriceBefore", before.salePrice())
				.detail("salePriceAfter", after.salePrice())
				.detail("unitCostBefore", before.unitCost())
				.detail("unitCostAfter", after.unitCost())
				.detail("chargeableByDefaultBefore", before.chargeableByDefault())
				.detail("chargeableByDefaultAfter", after.chargeableByDefault())
				.summary("Pricing of product " + product.getSku() + " changed"));
		}
		return ProductResponse.from(product, true);
	}

	/** For new movements: missing product is 404, inactive product is a 409 (BR-INV-004). */
	Product requireActive(long productId) {
		Product product = require(productId);
		if (!product.isActive()) {
			throw new ConflictException("PRODUCT_INACTIVE", "Product " + product.getSku() + " is inactive and cannot be moved.");
		}
		return product;
	}

	Product require(long productId) {
		return products.findById(productId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
	}

	/** A receipt like any other: locked stock row, immutable movement, audit (BR-INV-003). */
	private void recordInitialStock(CurrentUser user, Product product, Branch branch, int quantity, Instant now) {
		BranchStock stock = ledger.lock(product, branch).get(branch.getId());
		int delta = MovementType.RECEIPT.signedDelta(quantity);
		int balanceAfter = stock.apply(delta, now);
		// Server-side id: the unique SKU already turns a repeated create into a 409.
		UUID operationId = UUID.randomUUID();
		String fingerprint = OperationFingerprint.of("INITIAL_STOCK", branch.getId(), product.getId(), quantity);
		StockMovement movement = movements.saveAndFlush(new StockMovement(operationId, MovementType.RECEIPT, stock,
				delta, balanceAfter, INITIAL_STOCK_REASON, fingerprint, null,
				entityManager.getReference(AppUser.class, user.id()), now));
		audit.record(user, AuditEntry.of(AuditAction.STOCK_MOVEMENT_RECORDED, movement.getId())
			.branch(branch.getId())
			.operation(operationId)
			.detail("movementType", MovementType.RECEIPT)
			.detail("initialStock", true)
			.detail("quantity", quantity)
			.detail("sku", product.getSku())
			.detail("productName", product.getName())
			.detail("branchCode", branch.getCode())
			.detail("branchName", branch.getName())
			.detail("balanceBefore", movement.getBalanceBefore())
			.detail("balanceAfter", balanceAfter)
			.summary("Initial stock " + quantity + " x " + product.getSku() + " at " + branch.getCode()));
	}

	/** At most two decimals (validated at the HTTP boundary), kept with scale 2 like the column. */
	private static Pricing pricing(BigDecimal unitCost, BigDecimal salePrice, Boolean chargeableByDefault) {
		return new Pricing(money(unitCost), money(salePrice), !Boolean.FALSE.equals(chargeableByDefault));
	}

	static BigDecimal money(BigDecimal value) {
		return value == null ? null : value.setScale(2);
	}

	/** 15000 and 15000.00 are the same amount: compare values, not scales. */
	private static boolean samePricing(Pricing a, Pricing b) {
		return sameAmount(a.unitCost(), b.unitCost()) && sameAmount(a.salePrice(), b.salePrice())
				&& a.chargeableByDefault() == b.chargeableByDefault();
	}

	static boolean sameAmount(BigDecimal a, BigDecimal b) {
		return Objects.equals(a, b) || (a != null && b != null && a.compareTo(b) == 0);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
