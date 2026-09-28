package dev.jeffrojas.electronicarojas.repairs;

import java.time.Instant;

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

/** One entry of an order's timeline (BR-REP-003). Append-only in JPA and in the database. */
@Entity
@Immutable
@Table(name = "repair_status_history")
public class RepairStatusChange {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false, updatable = false)
	private RepairOrder order;

	/** NULL for the first entry (reception). */
	@Enumerated(EnumType.STRING)
	@Column(length = 30, updatable = false)
	private RepairStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30, updatable = false)
	private RepairStatus toStatus;

	@Column(length = 500, updatable = false)
	private String reason;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "actor_id", nullable = false, updatable = false)
	private AppUser actor;

	@Column(nullable = false, updatable = false)
	private Instant changedAt;

	protected RepairStatusChange() {
	}

	RepairStatusChange(RepairOrder order, RepairStatus fromStatus, RepairStatus toStatus, String reason, AppUser actor,
			Instant changedAt) {
		this.order = order;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.reason = reason;
		this.actor = actor;
		this.changedAt = changedAt;
	}

	public Long getId() {
		return id;
	}

	public RepairStatus getFromStatus() {
		return fromStatus;
	}

	public RepairStatus getToStatus() {
		return toStatus;
	}

	public String getReason() {
		return reason;
	}

	public AppUser getActor() {
		return actor;
	}

	public Instant getChangedAt() {
		return changedAt;
	}

}
