package dev.jeffrojas.electronicarojas.repairs;

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

import org.hibernate.annotations.Immutable;

import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * Correction of a consumed part (BR-REP-012): units given back to the shelf, with the reason and
 * the order's status at that moment. Append-only; the original usage is never rewritten.
 */
@Entity
@Immutable
@Table(name = "repair_part_returns")
public class RepairPartReturn {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usage_id", nullable = false, updatable = false)
	private RepairPartUsage usage;

	@Column(nullable = false, updatable = false)
	private int quantity;

	@Column(nullable = false, length = 300, updatable = false)
	private String reason;

	@Column(nullable = false, updatable = false)
	private UUID operationId;

	@Column(nullable = false, length = 64, updatable = false)
	private String requestFingerprint;

	@Column(nullable = false, updatable = false)
	private long movementId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30, updatable = false)
	private RepairStatus orderStatus;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recorded_by", nullable = false, updatable = false)
	private AppUser recordedBy;

	@Column(nullable = false, updatable = false)
	private Instant recordedAt;

	protected RepairPartReturn() {
	}

	RepairPartReturn(RepairPartUsage usage, int quantity, String reason, UUID operationId, String requestFingerprint,
			long movementId, RepairStatus orderStatus, AppUser recordedBy, Instant now) {
		this.usage = usage;
		this.quantity = quantity;
		this.reason = reason;
		this.operationId = operationId;
		this.requestFingerprint = requestFingerprint;
		this.movementId = movementId;
		this.orderStatus = orderStatus;
		this.recordedBy = recordedBy;
		this.recordedAt = now;
	}

	public Long getId() {
		return id;
	}

	public RepairPartUsage getUsage() {
		return usage;
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

	public RepairStatus getOrderStatus() {
		return orderStatus;
	}

	public AppUser getRecordedBy() {
		return recordedBy;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

}
