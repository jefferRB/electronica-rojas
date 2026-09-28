package dev.jeffrojas.electronicarojas.inventory;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Immutable;

import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * Immutable evidence of one stock change (BR-INV-003): who, when (UTC), where, what, how many,
 * why, and the operation it belongs to. {@code balanceAfter} makes every historic balance
 * readable without recomputing, and lets a replayed request return the original result.
 */
@Entity
@Immutable
@Table(name = "stock_movements")
public class StockMovement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private UUID operationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private MovementType type;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false, updatable = false)
	private Branch branch;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false, updatable = false)
	private Product product;

	@Column(nullable = false, updatable = false)
	private int quantityDelta;

	@Column(nullable = false, updatable = false)
	private int balanceAfter;

	@Column(length = 300, updatable = false)
	private String reason;

	@Column(nullable = false, length = 64, updatable = false)
	private String requestFingerprint;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "transfer_id", updatable = false)
	private StockTransfer transfer;

	/**
	 * Repair order of an OUT_FOR_REPAIR / RETURN_FROM_REPAIR movement. A plain id, not a relation:
	 * inventory does not depend on the repairs module (the database FK still guards it).
	 */
	@Column(updatable = false)
	private Long repairOrderId;

	/** Read-only code of that order for history screens, without a Java dependency on repairs. */
	@Formula("(select o.order_code from repair_orders o where o.id = repair_order_id)")
	private String repairOrderCode;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "actor_id", nullable = false, updatable = false)
	private AppUser actor;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected StockMovement() {
	}

	StockMovement(UUID operationId, MovementType type, BranchStock stock, int quantityDelta, int balanceAfter,
			String reason, String requestFingerprint, StockTransfer transfer, AppUser actor, Instant now) {
		this.operationId = operationId;
		this.type = type;
		this.branch = stock.getBranch();
		this.product = stock.getProduct();
		this.quantityDelta = quantityDelta;
		this.balanceAfter = balanceAfter;
		this.reason = reason;
		this.requestFingerprint = requestFingerprint;
		this.transfer = transfer;
		this.actor = actor;
		this.createdAt = now;
	}

	/** A movement of a repair order (see {@link RepairStock}). */
	StockMovement(UUID operationId, MovementType type, BranchStock stock, int quantityDelta, int balanceAfter,
			String reason, String requestFingerprint, long repairOrderId, AppUser actor, Instant now) {
		this(operationId, type, stock, quantityDelta, balanceAfter, reason, requestFingerprint, null, actor, now);
		this.repairOrderId = repairOrderId;
	}

	public Long getId() {
		return id;
	}

	public UUID getOperationId() {
		return operationId;
	}

	public MovementType getType() {
		return type;
	}

	public Branch getBranch() {
		return branch;
	}

	public Product getProduct() {
		return product;
	}

	public int getQuantityDelta() {
		return quantityDelta;
	}

	public int getBalanceAfter() {
		return balanceAfter;
	}

	public int getBalanceBefore() {
		return balanceAfter - quantityDelta;
	}

	public String getReason() {
		return reason;
	}

	String getRequestFingerprint() {
		return requestFingerprint;
	}

	public StockTransfer getTransfer() {
		return transfer;
	}

	public Long getRepairOrderId() {
		return repairOrderId;
	}

	public String getRepairOrderCode() {
		return repairOrderCode;
	}

	public AppUser getActor() {
		return actor;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
