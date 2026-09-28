package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
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
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.PreferredWindow;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.Province;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestChannel;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * A customer's request for a home visit (BR-SRV-001). Contact and address are a snapshot of what
 * was submitted; the link to a {@link Customer} record is set by staff after identity
 * resolution. It holds no visit time: that lives in {@link ServiceVisit}.
 */
@Entity
@Table(name = "service_requests")
public class ServiceRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20, updatable = false)
	private String requestCode;

	@Column(nullable = false, updatable = false)
	private UUID publicRef;

	@Column(nullable = false, updatable = false)
	private UUID submissionId;

	@Column(nullable = false, length = 64, updatable = false)
	private String submissionFingerprint;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private RequestChannel channel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RequestStatus status;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private Customer customer;

	@Column(nullable = false, length = 160, updatable = false)
	private String contactName;

	@Column(nullable = false, length = 16, updatable = false)
	private String contactPhone;

	@Column(length = 254, updatable = false)
	private String contactEmail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private Province province;

	@Column(nullable = false, length = 80, updatable = false)
	private String canton;

	@Column(length = 80, updatable = false)
	private String district;

	@Column(nullable = false, length = 300, updatable = false)
	private String addressLine;

	@Column(nullable = false, length = 60, updatable = false)
	private String deviceType;

	@Column(length = 60, updatable = false)
	private String brand;

	@Column(length = 80, updatable = false)
	private String model;

	@Column(nullable = false, length = 1000, updatable = false)
	private String problemDescription;

	@Column(updatable = false)
	private LocalDate preferredDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private PreferredWindow preferredWindow;

	@Column(length = 500, updatable = false)
	private String additionalNotes;

	@Column(nullable = false, updatable = false)
	private Instant contactConsentAt;

	@Column(nullable = false, updatable = false)
	private boolean notificationsConsent;

	/** V11: channel-specific opt-ins of the public form (e-mail requires an address). */
	@Column(nullable = false, updatable = false)
	private boolean emailConsent;

	@Column(nullable = false, updatable = false)
	private boolean whatsappConsent;

	@Column(length = 20, updatable = false)
	private String consentTextVersion;

	@Column(length = 500)
	private String decisionReason;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", updatable = false)
	private AppUser createdBy;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected ServiceRequest() {
	}

	/** Submitted data, already validated and normalized (phone in E.164). */
	record Submission(String contactName, String contactPhone, String contactEmail, Province province, String canton,
			String district, String addressLine, String deviceType, String brand, String model,
			String problemDescription, LocalDate preferredDate, PreferredWindow preferredWindow,
			String additionalNotes, boolean notificationsConsent, boolean emailConsent, boolean whatsappConsent,
			String consentTextVersion) {
	}

	ServiceRequest(String requestCode, UUID submissionId, String fingerprint, RequestChannel channel, Branch branch,
			Submission data, AppUser createdBy, Instant now) {
		this.requestCode = requestCode;
		this.publicRef = UUID.randomUUID();
		this.submissionId = submissionId;
		this.submissionFingerprint = fingerprint;
		this.channel = channel;
		this.status = RequestStatus.PENDING;
		this.branch = branch;
		this.contactName = data.contactName().strip().replaceAll("\\s+", " ");
		this.contactPhone = data.contactPhone();
		this.contactEmail = blankToNull(data.contactEmail()) == null ? null
				: data.contactEmail().strip().toLowerCase(Locale.ROOT);
		this.province = data.province();
		this.canton = data.canton().strip();
		this.district = blankToNull(data.district());
		this.addressLine = data.addressLine().strip();
		this.deviceType = data.deviceType().strip();
		this.brand = blankToNull(data.brand());
		this.model = blankToNull(data.model());
		this.problemDescription = data.problemDescription().strip();
		this.preferredDate = data.preferredDate();
		this.preferredWindow = data.preferredWindow() == null ? PreferredWindow.ANY : data.preferredWindow();
		this.additionalNotes = blankToNull(data.additionalNotes());
		this.contactConsentAt = now;
		this.notificationsConsent = data.notificationsConsent();
		this.emailConsent = data.emailConsent();
		this.whatsappConsent = data.whatsappConsent();
		this.consentTextVersion = data.emailConsent() || data.whatsappConsent() ? data.consentTextVersion() : null;
		this.createdBy = createdBy;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** Only called by the services after RequestPolicy approved the transition. */
	void moveTo(RequestStatus target, String reason, Instant now) {
		this.status = target;
		if (reason != null) {
			this.decisionReason = reason;
		}
		this.updatedAt = now;
	}

	void linkCustomer(Customer customer, Instant now) {
		this.customer = customer;
		this.updatedAt = now;
	}

	void assignBranch(Branch branch, Instant now) {
		this.branch = branch;
		this.updatedAt = now;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	public Long getId() {
		return id;
	}

	public String getRequestCode() {
		return requestCode;
	}

	public UUID getPublicRef() {
		return publicRef;
	}

	UUID getSubmissionId() {
		return submissionId;
	}

	String getSubmissionFingerprint() {
		return submissionFingerprint;
	}

	public RequestChannel getChannel() {
		return channel;
	}

	public RequestStatus getStatus() {
		return status;
	}

	public Branch getBranch() {
		return branch;
	}

	public Customer getCustomer() {
		return customer;
	}

	public String getContactName() {
		return contactName;
	}

	public String getContactPhone() {
		return contactPhone;
	}

	public String getContactEmail() {
		return contactEmail;
	}

	public Province getProvince() {
		return province;
	}

	public String getCanton() {
		return canton;
	}

	public String getDistrict() {
		return district;
	}

	public String getAddressLine() {
		return addressLine;
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

	public String getProblemDescription() {
		return problemDescription;
	}

	public LocalDate getPreferredDate() {
		return preferredDate;
	}

	public PreferredWindow getPreferredWindow() {
		return preferredWindow;
	}

	public String getAdditionalNotes() {
		return additionalNotes;
	}

	public Instant getContactConsentAt() {
		return contactConsentAt;
	}

	public boolean isNotificationsConsent() {
		return notificationsConsent;
	}

	public boolean isEmailConsent() {
		return emailConsent;
	}

	public boolean isWhatsappConsent() {
		return whatsappConsent;
	}

	public String getConsentTextVersion() {
		return consentTextVersion;
	}

	public String getDecisionReason() {
		return decisionReason;
	}

	public AppUser getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getVersion() {
		return version;
	}

}
