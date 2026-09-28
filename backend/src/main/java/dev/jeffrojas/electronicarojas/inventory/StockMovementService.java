package dev.jeffrojas.electronicarojas.inventory;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.MovementResponse;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.RecordMovementRequest;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.OperationFingerprint;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * FR-INV-004 / BR-INV-003: receipts, issues and adjustments at one branch, idempotent by
 * operationId. Only ADMIN (any active branch) and BRANCH_MANAGER (assigned branches) may record.
 */
@Service
public class StockMovementService {

	private final StockMovementRepository movements;

	private final StockTransferRepository transfers;

	private final BranchService branchService;

	private final ProductCatalogService catalog;

	private final StockLedger ledger;

	private final AuditService audit;

	private final EntityManager entityManager;

	private final Clock clock;

	StockMovementService(StockMovementRepository movements, StockTransferRepository transfers,
			BranchService branchService, ProductCatalogService catalog, StockLedger ledger, AuditService audit,
			EntityManager entityManager, Clock clock) {
		this.movements = movements;
		this.transfers = transfers;
		this.branchService = branchService;
		this.catalog = catalog;
		this.ledger = ledger;
		this.audit = audit;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
	@Transactional
	public OperationResult<MovementResponse> record(CurrentUser user, RecordMovementRequest request) {
		MovementType type = request.type();
		if (!type.isStandalone()) {
			throw new InvalidRequestException("type", "TRANSFER_TYPE_NOT_ALLOWED",
					"transfer and repair movements are created by their own operations");
		}
		String reason = request.reason().strip();
		String fingerprint = OperationFingerprint.of("MOVEMENT", type, request.branchId(), request.productId(),
				request.quantity(), reason);

		// Fast path for a retry: return the stored result without taking any lock.
		Optional<MovementResponse> replay = findReplay(user, request, fingerprint);
		if (replay.isPresent()) {
			return OperationResult.replayed(replay.get());
		}

		Branch branch = branchService.requireOperable(user, request.branchId());
		Product product = catalog.requireActive(request.productId());
		BranchStock stock = ledger.lock(product, branch).get(branch.getId());

		// Checked again under the row lock: a concurrent duplicate that committed while we were
		// waiting is visible now, so it is returned instead of being applied twice.
		replay = findReplay(user, request, fingerprint);
		if (replay.isPresent()) {
			return OperationResult.replayed(replay.get());
		}

		Instant now = clock.instant();
		int delta = type.signedDelta(request.quantity());
		int balanceAfter = stock.apply(delta, now);
		StockMovement movement = movements.saveAndFlush(new StockMovement(request.operationId(), type, stock, delta,
				balanceAfter, reason, fingerprint, null, entityManager.getReference(AppUser.class, user.id()), now));
		audit.record(user, AuditEntry.of(AuditAction.STOCK_MOVEMENT_RECORDED, movement.getId())
			.branch(branch.getId())
			.operation(request.operationId())
			.detail("movementType", type)
			.detail("quantity", request.quantity())
			.detail("sku", product.getSku())
			.detail("productName", product.getName())
			.detail("branchCode", branch.getCode())
			.detail("branchName", branch.getName())
			.detail("balanceBefore", movement.getBalanceBefore())
			.detail("balanceAfter", balanceAfter)
			.summary(type + " " + delta + " x " + product.getSku() + " at " + branch.getCode() + " (balance "
					+ movement.getBalanceBefore() + " -> " + balanceAfter + ")"));
		return OperationResult.created(MovementResponse.from(movement));
	}

	/**
	 * Same operationId + same payload + same actor = replay. Anything else reusing the id is a
	 * conflict (BR-TRF-003), including an id already used by a transfer.
	 */
	private Optional<MovementResponse> findReplay(CurrentUser user, RecordMovementRequest request, String fingerprint) {
		Optional<StockMovement> existing = movements.findByOperationIdAndTransferIsNull(request.operationId());
		if (existing.isEmpty()) {
			if (transfers.existsByOperationId(request.operationId())) {
				throw reusedOperationId();
			}
			return Optional.empty();
		}
		StockMovement movement = existing.get();
		if (!movement.getRequestFingerprint().equals(fingerprint) || movement.getActor().getId() != user.id()) {
			throw reusedOperationId();
		}
		// A replay never reveals more than the user may currently read.
		branchService.requireReadable(user, movement.getBranch().getId());
		return Optional.of(MovementResponse.from(movement));
	}

	private static ConflictException reusedOperationId() {
		return new ConflictException("OPERATION_ID_REUSED",
				"This operationId was already used for a different operation.");
	}

}
