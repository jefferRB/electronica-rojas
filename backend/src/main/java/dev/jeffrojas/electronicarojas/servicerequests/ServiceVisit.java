package dev.jeffrojas.electronicarojas.servicerequests;

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
import jakarta.persistence.Version;

import dev.jeffrojas.electronicarojas.repairs.RepairOrder;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitOutcome;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * A visit scheduled for a request: the single source of truth for its time (BR-SRV-003).
 * {@code blockedUntil} = end + the branch's margin; CONFIRMED and IN_PROGRESS visits of one
 * technician can never overlap on [start, blockedUntil) (V9 exclusion constraint).
 */
@Entity
@Table(name = "service_visits")
public class ServiceVisit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false, updatable = false)
	private ServiceRequest request;

	@Column(nullable = false, updatable = false)
	private UUID operationId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "technician_id", nullable = false)
	private AppUser technician;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VisitStatus status;

	@Column(nullable = false)
	private Instant scheduledStart;

	@Column(nullable = false)
	private Instant scheduledEnd;

	@Column(nullable = false)
	private Instant blockedUntil;

	private Instant startedAt;

	private Instant completedAt;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private VisitOutcome outcome;

	@Column(length = 1000)
	private String outcomeNotes;

	@Column(length = 500)
	private String cancelReason;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "repair_order_id")
	private RepairOrder repairOrder;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false, updatable = false)
	private AppUser createdBy;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected ServiceVisit() {
	}

	ServiceVisit(ServiceRequest request, UUID operationId, AppUser technician, VisitStatus status, Slot slot,
			AppUser createdBy, Instant now) {
		this.request = request;
		this.operationId = operationId;
		this.technician = technician;
		this.status = status;
		applySlot(slot);
		this.createdBy = createdBy;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** A visit's time: [start, end) plus the margin that keeps the technician busy until blockedUntil. */
	record Slot(Instant start, Instant end, Instant blockedUntil) {
	}

	void reschedule(AppUser newTechnician, Slot slot, Instant now) {
		this.technician = newTechnician;
		applySlot(slot);
		this.updatedAt = now;
	}

	void moveTo(VisitStatus target, Instant now) {
		this.status = target;
		if (target == VisitStatus.IN_PROGRESS) {
			this.startedAt = now;
		}
		this.updatedAt = now;
	}

	void cancel(String reason, Instant now) {
		this.status = VisitStatus.CANCELLED;
		this.cancelReason = reason;
		this.updatedAt = now;
	}

	void complete(VisitOutcome outcome, String notes, Instant now) {
		this.status = VisitStatus.COMPLETED;
		this.outcome = outcome;
		this.outcomeNotes = notes == null || notes.isBlank() ? null : notes.strip();
		this.completedAt = now;
		this.updatedAt = now;
	}

	void linkRepairOrder(RepairOrder order, Instant now) {
		this.repairOrder = order;
		this.updatedAt = now;
	}

	boolean isActive() {
		return status == VisitStatus.PROPOSED || status == VisitStatus.CONFIRMED || status == VisitStatus.IN_PROGRESS;
	}

	Slot slot() {
		return new Slot(scheduledStart, scheduledEnd, blockedUntil);
	}

	private void applySlot(Slot slot) {
		this.scheduledStart = slot.start();
		this.scheduledEnd = slot.end();
		this.blockedUntil = slot.blockedUntil();
	}

	public Long getId() {
		return id;
	}

	public ServiceRequest getRequest() {
		return request;
	}

	UUID getOperationId() {
		return operationId;
	}

	public AppUser getTechnician() {
		return technician;
	}

	public VisitStatus getStatus() {
		return status;
	}

	public Instant getScheduledStart() {
		return scheduledStart;
	}

	public Instant getScheduledEnd() {
		return scheduledEnd;
	}

	public Instant getBlockedUntil() {
		return blockedUntil;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public VisitOutcome getOutcome() {
		return outcome;
	}

	public String getOutcomeNotes() {
		return outcomeNotes;
	}

	public String getCancelReason() {
		return cancelReason;
	}

	public RepairOrder getRepairOrder() {
		return repairOrder;
	}

	public AppUser getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public long getVersion() {
		return version;
	}

}
