package dev.jeffrojas.electronicarojas.repairs;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.SparePartOption;
import dev.jeffrojas.electronicarojas.inventory.OperationResult;
import dev.jeffrojas.electronicarojas.inventory.Product;
import dev.jeffrojas.electronicarojas.inventory.RepairStock;
import dev.jeffrojas.electronicarojas.inventory.RepairStock.StockChange;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.ConsumePartRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.RepairOrderDetail;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.ReturnPartRequest;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.OperationFingerprint;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * Spare parts used in a repair (BR-REP-007, BR-REP-011..013).
 * <p>
 * Each use case is ONE transaction: the part line (or return), the stock movement written by the
 * inventory's {@link RepairStock} (through its StockLedger) and the audit event commit together or
 * not at all. Locks are always taken in the same order: the repair order row, then (for returns)
 * the part line, then the stock row. Stock movements and transfers lock only stock rows and status
 * changes only the order row, so no two operations ever wait for each other in opposite orders.
 * <p>
 * Idempotency: the client sends one {@code operationId} per user intent. The replay check runs
 * under the order lock, so a double click waits for the first request and then gets its result
 * instead of consuming twice; UNIQUE constraints on the operation ids back it in the database.
 * <p>
 * Pricing (BR-REP-014): each new line freezes the catalog's sale price and cost at that moment, and
 * whether it is charged. Management may set another price or charging decision for the line; the
 * catalog is never changed from here.
 */
@Service
public class RepairPartService {

	static final int MAX_PAGE_SIZE = 50;

	private final RepairOrderAccess access;

	private final RepairPartUsageRepository usages;

	private final RepairPartReturnRepository returns;

	private final RepairDetailAssembler assembler;

	private final RepairStock repairStock;

	private final BranchService branchService;

	private final AuditService audit;

	private final EntityManager entityManager;

	private final Clock clock;

	RepairPartService(RepairOrderAccess access, RepairPartUsageRepository usages, RepairPartReturnRepository returns,
			RepairDetailAssembler assembler, RepairStock repairStock, BranchService branchService, AuditService audit,
			EntityManager entityManager, Clock clock) {
		this.access = access;
		this.usages = usages;
		this.returns = returns;
		this.assembler = assembler;
		this.repairStock = repairStock;
		this.branchService = branchService;
		this.audit = audit;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	/**
	 * Spare parts of the order's branch with their stock, for the consumption form. Only for whoever
	 * may use parts on this order: this is a technician's only window into inventory.
	 */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'TECHNICIAN')")
	@Transactional(readOnly = true)
	public PageResponse<SparePartOption> options(CurrentUser user, long orderId, String search, int page, int size) {
		RepairOrder order = access.requireVisible(user, orderId);
		if (!RepairPolicy.isPartsActor(user, order)) {
			throw new AccessDeniedException("Not allowed to use spare parts on this order");
		}
		return repairStock.spareParts(order.getBranch().getId(), search, page, size);
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'TECHNICIAN')")
	@Transactional
	public OperationResult<RepairOrderDetail> consume(CurrentUser user, long orderId, ConsumePartRequest request) {
		RepairOrder order = access.requireVisibleForUpdate(user, orderId);
		String note = blankToNull(request.note());
		BigDecimal requestedPrice = request.unitPrice() == null ? null : request.unitPrice().setScale(2);
		String fingerprint = consumeFingerprint(orderId, request, note, requestedPrice);
		Optional<RepairPartUsage> replay = usages.findByOperationId(request.operationId());
		if (replay.isPresent()) {
			requireSameOperation(replay.get().getRequestFingerprint(), fingerprint, replay.get().getRecordedBy(), user);
			return OperationResult.replayed(assembler.detail(user, order));
		}
		if (!RepairPolicy.isPartsActor(user, order)) {
			throw new AccessDeniedException("Not allowed to use spare parts on this order");
		}
		if (!RepairPolicy.mayConsumeParts(user, order)) {
			throw new ConflictException("PARTS_NOT_ALLOWED",
					"Spare parts can only be recorded while the order is IN_REPAIR.",
					Map.of("status", order.getStatus().name()));
		}
		// Parts come from the order's own branch; parts elsewhere are transferred first (BR-REP-011).
		Branch branch = branchService.requireOperable(user, order.getBranch().getId());
		Product product = repairStock.requireSparePart(request.productId());
		PartCharges.LinePricing pricing = PartCharges.decide(product.getSalePrice(), product.isChargeableByDefault(),
				requestedPrice, request.chargeable());
		if (pricing.overridesDefaults() && !RepairPolicy.mayOverridePartPricing(user)) {
			throw new AccessDeniedException("Only management may change the price or the charge of a part line");
		}
		StockChange change = repairStock.consume(user.id(), branch, product, request.quantity(), order.getId(),
				request.operationId(), note, fingerprint);
		Instant now = clock.instant();
		RepairPartUsage usage = usages.saveAndFlush(new RepairPartUsage(order, product, branch, request.quantity(),
				pricing, request.operationId(), fingerprint, change.movementId(), note, actor(user), now));
		// Prices and the charging decision are business data; the cost stays out of the trail.
		audit.record(user, AuditEntry.of(AuditAction.REPAIR_PART_CONSUMED, usage.getId())
			.branch(branch.getId())
			.operation(request.operationId())
			.detail("orderCode", order.getOrderCode())
			.detail("sku", product.getSku())
			.detail("productName", product.getName())
			.detail("quantity", request.quantity())
			.detail("balanceBefore", change.balanceBefore())
			.detail("balanceAfter", change.balanceAfter())
			.detail("branchCode", branch.getCode())
			.detail("branchName", branch.getName())
			.detail("unitPrice", pricing.unitPrice())
			.detail("chargeable", pricing.chargeable())
			.detail("catalogPrice", product.getSalePrice())
			.detail("priceOverridden", pricing.priceOverridden())
			.detail("chargeOverridden", pricing.chargeOverridden())
			.summary(request.quantity() + " x " + product.getSku() + " used in order " + order.getOrderCode() + " at "
					+ branch.getCode() + " (balance " + change.balanceBefore() + " -> " + change.balanceAfter() + ")"
					+ (pricing.chargeable() ? "" : ", not charged") + (pricing.priceOverridden() ? ", price set" : "")));
		return OperationResult.created(assembler.detail(user, order));
	}

	/**
	 * Same as before pricing existed when no price or charge is sent, so a retry of an operation
	 * recorded earlier still matches; the pricing choice is part of the operation otherwise.
	 */
	private static String consumeFingerprint(long orderId, ConsumePartRequest request, String note,
			BigDecimal requestedPrice) {
		if (requestedPrice == null && request.chargeable() == null) {
			return OperationFingerprint.of("REPAIR_PART", orderId, request.productId(), request.quantity(), note);
		}
		return OperationFingerprint.of("REPAIR_PART", orderId, request.productId(), request.quantity(), note, "PRICING",
				requestedPrice == null ? null : requestedPrice.toPlainString(), request.chargeable());
	}

	/**
	 * Gives back units of a consumed part (partial or complete). The usage line keeps what was
	 * consumed; the running total of returns can never exceed it (row lock + V10 CHECK).
	 */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'TECHNICIAN')")
	@Transactional
	public OperationResult<RepairOrderDetail> returnPart(CurrentUser user, long orderId, long usageId,
			ReturnPartRequest request) {
		RepairOrder order = access.requireVisibleForUpdate(user, orderId);
		String reason = request.reason().strip();
		String fingerprint = OperationFingerprint.of("REPAIR_PART_RETURN", orderId, usageId, request.quantity(), reason);
		Optional<RepairPartReturn> replay = returns.findByOperationId(request.operationId());
		if (replay.isPresent()) {
			requireSameOperation(replay.get().getRequestFingerprint(), fingerprint, replay.get().getRecordedBy(), user);
			return OperationResult.replayed(assembler.detail(user, order));
		}
		if (!RepairPolicy.mayReturnParts(user, order)) {
			throw new AccessDeniedException("Not allowed to correct spare parts of this order");
		}
		RepairPartUsage usage = usages.lockByIdAndOrderId(usageId, order.getId())
			.orElseThrow(() -> new NotFoundException("Part line not found."));
		if (request.quantity() > usage.remainingQuantity()) {
			throw new ConflictException("RETURN_EXCEEDS_CONSUMED",
					"Only " + usage.remainingQuantity() + " unit(s) of this line are still counted as used.",
					Map.of("consumed", usage.getQuantity(), "returned", usage.getReturnedQuantity(), "remaining",
							usage.remainingQuantity()));
		}
		Branch branch = branchService.requireOperable(user, usage.getBranch().getId());
		StockChange change = repairStock.restock(user.id(), branch, usage.getProduct().getId(), request.quantity(),
				order.getId(), request.operationId(), reason, fingerprint);
		usage.registerReturn(request.quantity());
		usages.saveAndFlush(usage);
		boolean closed = RepairPolicy.isClosed(order);
		RepairPartReturn saved = returns.saveAndFlush(new RepairPartReturn(usage, request.quantity(), reason,
				request.operationId(), fingerprint, change.movementId(), order.getStatus(), actor(user), clock.instant()));
		// The reason stays on the order (it is free text); the audit keeps codes and quantities.
		audit.record(user, AuditEntry.of(AuditAction.REPAIR_PART_RETURNED, saved.getId())
			.branch(branch.getId())
			.operation(request.operationId())
			.detail("orderCode", order.getOrderCode())
			.detail("orderStatus", order.getStatus())
			.detail("orderClosed", closed)
			.detail("sku", change.product().getSku())
			.detail("productName", change.product().getName())
			.detail("quantity", request.quantity())
			.detail("remainingQuantity", usage.remainingQuantity())
			.detail("balanceBefore", change.balanceBefore())
			.detail("balanceAfter", change.balanceAfter())
			.detail("branchCode", branch.getCode())
			.detail("branchName", branch.getName())
			.summary(request.quantity() + " x " + change.product().getSku() + " returned from order "
					+ order.getOrderCode() + (closed ? " (closed order)" : "")));
		return OperationResult.created(assembler.detail(user, order));
	}

	/** Same id + same data + same author = the stored result; anything else reusing it is a 409. */
	private static void requireSameOperation(String storedFingerprint, String fingerprint, AppUser storedActor,
			CurrentUser user) {
		if (!storedFingerprint.equals(fingerprint) || storedActor.getId() != user.id()) {
			throw new ConflictException("OPERATION_ID_REUSED",
					"This operationId was already used for a different operation.");
		}
	}

	private AppUser actor(CurrentUser user) {
		return entityManager.getReference(AppUser.class, user.id());
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
