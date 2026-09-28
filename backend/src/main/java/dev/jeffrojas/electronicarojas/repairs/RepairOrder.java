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
import jakarta.persistence.Version;

import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.customers.Customer;
import dev.jeffrojas.electronicarojas.repairs.RepairEnums.Resolution;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * A customer's appliance in the company's custody (BR-REP-001). It is not inventory: nothing here
 * touches products or stock. Status only changes through {@link RepairWorkflow}.
 */
@Entity
@Table(name = "repair_orders")
public class RepairOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20, updatable = false)
	private String orderCode;

	@Column(nullable = false, updatable = false)
	private UUID intakeOperationId;

	@Column(nullable = false, length = 64, updatable = false)
	private String intakeFingerprint;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false, updatable = false)
	private Customer customer;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false, updatable = false)
	private Branch branch;

	@Column(nullable = false, length = 60, updatable = false)
	private String deviceType;

	@Column(nullable = false, length = 60, updatable = false)
	private String brand;

	@Column(length = 80, updatable = false)
	private String model;

	@Column(length = 80, updatable = false)
	private String serialNumber;

	@Column(nullable = false, length = 1000, updatable = false)
	private String reportedFault;

	@Column(nullable = false, length = 1000, updatable = false)
	private String physicalCondition;

	@Column(length = 500, updatable = false)
	private String accessories;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RepairStatus status;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private Resolution resolution;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_technician_id")
	private AppUser assignedTechnician;

	@Column(length = 2000)
	private String diagnosis;

	private Instant diagnosisUpdatedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "diagnosis_updated_by")
	private AppUser diagnosisUpdatedBy;

	@Column(nullable = false, updatable = false)
	private Instant receivedAt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "received_by", nullable = false, updatable = false)
	private AppUser receivedBy;

	private Instant deliveredAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "delivered_by")
	private AppUser deliveredBy;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected RepairOrder() {
	}

	RepairOrder(String orderCode, UUID intakeOperationId, String intakeFingerprint, Customer customer, Branch branch,
			Intake intake, AppUser receivedBy, Instant now) {
		this.orderCode = orderCode;
		this.intakeOperationId = intakeOperationId;
		this.intakeFingerprint = intakeFingerprint;
		this.customer = customer;
		this.branch = branch;
		this.deviceType = intake.deviceType().strip();
		this.brand = intake.brand().strip();
		this.model = blankToNull(intake.model());
		this.serialNumber = blankToNull(intake.serialNumber());
		this.reportedFault = intake.reportedFault().strip();
		this.physicalCondition = intake.physicalCondition().strip();
		this.accessories = blankToNull(intake.accessories());
		this.status = RepairStatus.RECEIVED;
		this.receivedAt = now;
		this.receivedBy = receivedBy;
		this.updatedAt = now;
	}

	/** Appliance data captured at reception; immutable afterwards (custody evidence). */
	record Intake(String deviceType, String brand, String model, String serialNumber, String reportedFault,
			String physicalCondition, String accessories) {
	}

	/** Only called by RepairWorkflow, after the policy checks and under a row lock. */
	void moveTo(RepairStatus target, Resolution newResolution, AppUser actor, Instant now) {
		this.status = target;
		if (newResolution != null) {
			this.resolution = newResolution;
		}
		if (target == RepairStatus.DELIVERED) {
			this.deliveredAt = now;
			this.deliveredBy = actor;
		}
		this.updatedAt = now;
	}

	void assignTechnician(AppUser technician, Instant now) {
		this.assignedTechnician = technician;
		this.updatedAt = now;
	}

	void recordDiagnosis(String text, AppUser author, Instant now) {
		this.diagnosis = text.strip();
		this.diagnosisUpdatedAt = now;
		this.diagnosisUpdatedBy = author;
		this.updatedAt = now;
	}

	boolean isInCustody() {
		return deliveredAt == null;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	public Long getId() {
		return id;
	}

	public String getOrderCode() {
		return orderCode;
	}

	UUID getIntakeOperationId() {
		return intakeOperationId;
	}

	String getIntakeFingerprint() {
		return intakeFingerprint;
	}

	public Customer getCustomer() {
		return customer;
	}

	public Branch getBranch() {
		return branch;
	}

	public String getDeviceType() {
		return deviceType;
	}

	public String getBrand() {
		return brand;
	}

	public String getModel() {
		return model;
	}

	public String getSerialNumber() {
		return serialNumber;
	}

	public String getReportedFault() {
		return reportedFault;
	}

	public String getPhysicalCondition() {
		return physicalCondition;
	}

	public String getAccessories() {
		return accessories;
	}

	public RepairStatus getStatus() {
		return status;
	}

	public Resolution getResolution() {
		return resolution;
	}

	public AppUser getAssignedTechnician() {
		return assignedTechnician;
	}

	public String getDiagnosis() {
		return diagnosis;
	}

	public Instant getDiagnosisUpdatedAt() {
		return diagnosisUpdatedAt;
	}

	public AppUser getDiagnosisUpdatedBy() {
		return diagnosisUpdatedBy;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}

	public AppUser getReceivedBy() {
		return receivedBy;
	}

	public Instant getDeliveredAt() {
		return deliveredAt;
	}

	public AppUser getDeliveredBy() {
		return deliveredBy;
	}

	public long getVersion() {
		return version;
	}

}
