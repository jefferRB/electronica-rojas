package dev.jeffrojas.electronicarojas.inventory;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * Header of an immediate branch-to-branch transfer (ARCH-DB-003). Its UNIQUE operationId makes
 * the request idempotent; {@code requestFingerprint} detects a reused id with a different payload.
 */
@Entity
@Immutable
@Table(name = "stock_transfers")
public class StockTransfer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private UUID operationId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "source_branch_id", nullable = false, updatable = false)
	private Branch sourceBranch;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "destination_branch_id", nullable = false, updatable = false)
	private Branch destinationBranch;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false, updatable = false)
	private Product product;

	@Column(nullable = false, updatable = false)
	private int quantity;

	@Column(length = 300, updatable = false)
	private String reason;

	@Column(nullable = false, length = 64, updatable = false)
	private String requestFingerprint;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "actor_id", nullable = false, updatable = false)
	private AppUser actor;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected StockTransfer() {
	}

	StockTransfer(UUID operationId, Branch source, Branch destination, Product product, int quantity, String reason,
			String requestFingerprint, AppUser actor, Instant now) {
		this.operationId = operationId;
		this.sourceBranch = source;
		this.destinationBranch = destination;
		this.product = product;
		this.quantity = quantity;
		this.reason = reason;
		this.requestFingerprint = requestFingerprint;
		this.actor = actor;
		this.createdAt = now;
	}

	public Long getId() {
		return id;
	}

	public UUID getOperationId() {
		return operationId;
	}

	public Branch getSourceBranch() {
		return sourceBranch;
	}

	public Branch getDestinationBranch() {
		return destinationBranch;
	}

	public Product getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}

	public String getReason() {
		return reason;
	}

	String getRequestFingerprint() {
		return requestFingerprint;
	}

	public AppUser getActor() {
		return actor;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
