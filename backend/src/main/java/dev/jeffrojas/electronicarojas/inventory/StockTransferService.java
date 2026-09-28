package dev.jeffrojas.electronicarojas.inventory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.ActorSummary;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.CreateTransferRequest;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.MovementResponse;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.ProductSummary;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.TransferResponse;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.OperationFingerprint;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * Immediate transfer between two branches (BR-TRF-001..005, ARCH-DB-002..005).
 * <p>
 * One database transaction: lock both stock rows in branch-id order, debit the source, credit the
 * destination, write the header and both movements, write the audit trail. Any exception rolls
 * everything back, so there is never a half transfer.
 */
@Service
public class StockTransferService {

	private static final String NOT_FOUND = "Transfer not found.";

	private final StockTransferRepository transfers;

	private final StockMovementRepository movements;

	private final BranchService branchService;

	private final ProductCatalogService catalog;

	private final StockLedger ledger;

	private final AuditService audit;

	private final EntityManager entityManager;

	private final Clock clock;

	StockTransferService(StockTransferRepository transfers, StockMovementRepository movements,
			BranchService branchService, ProductCatalogService catalog, StockLedger ledger, AuditService audit,
			EntityManager entityManager, Clock clock) {
		this.transfers = transfers;
		this.movements = movements;
		this.branchService = branchService;
		this.catalog = catalog;
		this.ledger = ledger;
		this.audit = audit;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
	@Transactional
	public OperationResult<TransferResponse> transfer(CurrentUser user, CreateTransferRequest request) {
		if (request.sourceBranchId().equals(request.destinationBranchId())) {
			throw new InvalidRequestException("destinationBranchId", "SAME_BRANCH", "must be different from the source branch");
		}
		String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();
		String fingerprint = OperationFingerprint.of("TRANSFER", request.sourceBranchId(),
				request.destinationBranchId(), request.productId(), request.quantity(), reason);

		Optional<TransferResponse> replay = findReplay(user, request, fingerprint);
		if (replay.isPresent()) {
			return OperationResult.replayed(replay.get());
		}

		// BR-TRF-001: the actor must be allowed to operate BOTH branches, and both must be active.
		Branch source = branchService.requireOperable(user, request.sourceBranchId());
		Branch destination = branchService.requireOperable(user, request.destinationBranchId());
		Product product = catalog.requireActive(request.productId());

		// Locks are always taken in ascending branch id, whatever the transfer direction, so
		// A->B and B->A running at the same time wait for each other instead of deadlocking.
		Map<Long, BranchStock> locked = ledger.lock(product, source, destination);

		replay = findReplay(user, request, fingerprint);
		if (replay.isPresent()) {
			return OperationResult.replayed(replay.get());
		}

		Instant now = clock.instant();
		BranchStock sourceStock = locked.get(source.getId());
		BranchStock destinationStock = locked.get(destination.getId());
		int quantity = request.quantity();
		int sourceBalance = sourceStock.apply(MovementType.TRANSFER_OUT.signedDelta(quantity), now);
		int destinationBalance = destinationStock.apply(MovementType.TRANSFER_IN.signedDelta(quantity), now);

		AppUser actor = entityManager.getReference(AppUser.class, user.id());
		StockTransfer transfer = transfers.save(new StockTransfer(request.operationId(), source, destination, product,
				quantity, reason, fingerprint, actor, now));
		StockMovement out = new StockMovement(request.operationId(), MovementType.TRANSFER_OUT, sourceStock, -quantity,
				sourceBalance, reason, fingerprint, transfer, actor, now);
		StockMovement in = new StockMovement(request.operationId(), MovementType.TRANSFER_IN, destinationStock,
				quantity, destinationBalance, reason, fingerprint, transfer, actor, now);
		movements.saveAll(List.of(out, in));
		movements.flush();

		String summary = "Transfer of " + quantity + " x " + product.getSku() + " from " + source.getCode() + " to "
				+ destination.getCode();
		// One event per branch so each branch's managers see it in their own audit scope.
		audit.record(user, transferAudit(transfer, product, source, destination, "SENT", sourceBalance)
			.summary(summary + " (sent, balance " + sourceBalance + ")"));
		audit.record(user, transferAudit(transfer, product, source, destination, "RECEIVED", destinationBalance)
			.summary(summary + " (received, balance " + destinationBalance + ")"));

		return OperationResult.created(toResponse(transfer, List.of(out, in)));
	}

	private static AuditEntry.Builder transferAudit(StockTransfer transfer, Product product, Branch source,
			Branch destination, String direction, int balanceAfter) {
		return AuditEntry.of(AuditAction.STOCK_TRANSFER_COMPLETED, transfer.getId())
			.branch("SENT".equals(direction) ? source.getId() : destination.getId())
			.operation(transfer.getOperationId())
			.detail("direction", direction)
			.detail("quantity", transfer.getQuantity())
			.detail("sku", product.getSku())
			.detail("productName", product.getName())
			.detail("sourceBranchCode", source.getCode())
			.detail("sourceBranchName", source.getName())
			.detail("destinationBranchCode", destination.getCode())
			.detail("destinationBranchName", destination.getName())
			.detail("balanceAfter", balanceAfter);
	}

	/** Visible to ADMIN, and to managers assigned to the source or the destination branch. */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
	@Transactional(readOnly = true)
	public TransferResponse get(CurrentUser user, long transferId) {
		StockTransfer transfer = transfers.findById(transferId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
		if (!canRead(user, transfer)) {
			throw new NotFoundException(NOT_FOUND);
		}
		return toResponse(transfer, movements.findByTransfer(transfer.getId()));
	}

	private Optional<TransferResponse> findReplay(CurrentUser user, CreateTransferRequest request, String fingerprint) {
		Optional<StockTransfer> existing = transfers.findByOperationId(request.operationId());
		if (existing.isEmpty()) {
			if (movements.findByOperationIdAndTransferIsNull(request.operationId()).isPresent()) {
				throw reusedOperationId();
			}
			return Optional.empty();
		}
		StockTransfer transfer = existing.get();
		if (!transfer.getRequestFingerprint().equals(fingerprint) || transfer.getActor().getId() != user.id()) {
			throw reusedOperationId();
		}
		if (!canRead(user, transfer)) {
			throw new NotFoundException(NOT_FOUND);
		}
		return Optional.of(toResponse(transfer, movements.findByTransfer(transfer.getId())));
	}

	private boolean canRead(CurrentUser user, StockTransfer transfer) {
		return canRead(user, transfer.getSourceBranch()) || canRead(user, transfer.getDestinationBranch());
	}

	private boolean canRead(CurrentUser user, Branch branch) {
		try {
			branchService.requireReadable(user, branch.getId());
			return true;
		}
		catch (NotFoundException ex) {
			return false;
		}
	}

	private static TransferResponse toResponse(StockTransfer transfer, List<StockMovement> legs) {
		List<MovementResponse> movementResponses = legs.stream().map(MovementResponse::from).toList();
		int sourceBalance = balanceOf(legs, MovementType.TRANSFER_OUT);
		int destinationBalance = balanceOf(legs, MovementType.TRANSFER_IN);
		return new TransferResponse(transfer.getId(), transfer.getOperationId(),
				ProductSummary.from(transfer.getProduct()), BranchSummary.from(transfer.getSourceBranch()),
				BranchSummary.from(transfer.getDestinationBranch()), transfer.getQuantity(), transfer.getReason(),
				sourceBalance, destinationBalance, ActorSummary.from(transfer.getActor()), transfer.getCreatedAt(),
				movementResponses);
	}

	private static int balanceOf(List<StockMovement> legs, MovementType type) {
		return legs.stream()
			.filter(leg -> leg.getType() == type)
			.findFirst()
			.map(StockMovement::getBalanceAfter)
			.orElseThrow(() -> new IllegalStateException("Transfer without " + type + " movement"));
	}

	private static ConflictException reusedOperationId() {
		return new ConflictException("OPERATION_ID_REUSED",
				"This operationId was already used for a different operation.");
	}

}
