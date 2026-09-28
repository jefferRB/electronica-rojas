package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.notifications.NotificationDtos.NotificationStatus;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.EventType;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.PreferredWindow;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.Province;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestChannel;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitOutcome;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus;

/** HTTP contracts of the home-service module (ENG-031). Entities never leave the services. */
public final class ServiceDtos {

	private ServiceDtos() {
	}

	// ---- Public (anonymous) ----

	/** The branches a customer can choose from: name only. */
	public record PublicBranch(long id, String name) {
	}

	/**
	 * The public form. {@code website} is a honeypot: invisible to people, filled by naive bots.
	 * {@code submissionId} is generated once per form by the browser: retries create one request.
	 */
	public record PublicSubmission(
			@NotNull UUID submissionId,
			@NotNull Long branchId,
			@NotBlank @Size(max = 160) String contactName,
			@NotBlank @Size(max = 30) String contactPhone,
			@Email @Size(max = 254) String contactEmail,
			@NotNull Province province,
			@NotBlank @Size(max = 80) String canton,
			@Size(max = 80) String district,
			@NotBlank @Size(max = 300) String addressLine,
			@NotBlank @Size(max = 60) String deviceType,
			@Size(max = 60) String brand,
			@Size(max = 80) String model,
			@NotBlank @Size(min = 10, max = 1000) String problemDescription,
			LocalDate preferredDate,
			PreferredWindow preferredWindow,
			@Size(max = 500) String additionalNotes,
			@NotNull @AssertTrue Boolean contactConsent,
			Boolean emailNotifications,
			Boolean whatsappNotifications,
			@Size(max = 20) String consentTextVersion,
			@Size(max = 200) String website) {
	}

	/**
	 * What an anonymous visitor needs to render the portal page (BR-SRV-009) and nothing more: no
	 * version, no editor, no previous slugs. While the portal is off, {@code accepting} is false and
	 * the rules and branches are left out. {@code slug} is the current one, also when the page was
	 * opened through a previous address.
	 */
	public record PublicPortal(String slug, boolean accepting, String welcomeMessage, String successMessage,
			PublicPortalRules rules, List<PublicBranch> branches) {
	}

	/** Dates are local days in Costa Rica, already computed by the server (min notice, max ahead). */
	public record PublicPortalRules(boolean allowPreferredDate, boolean allowPreferredWindow, LocalDate earliestDate,
			LocalDate latestDate, List<Integer> serviceDays, List<Province> servedProvinces, List<String> serviceTypes) {
	}

	/** What the customer sees after submitting: the reference to follow the request, nothing else. */
	public record PublicReceipt(UUID publicRef, String requestCode, PublicStatus status) {
	}

	/** Customer-facing status, deliberately coarser than the internal one. */
	public enum PublicStatus {
		RECEIVED, IN_REVIEW, SCHEDULED, IN_PROGRESS, COMPLETED, NOT_ACCEPTED, CANCELLED
	}

	/** Status page: no names, phone, address or technician; only what the customer needs. */
	public record PublicRequestStatus(String requestCode, PublicStatus status, String branchName, String deviceType,
			LocalDate preferredDate, PreferredWindow preferredWindow, LocalDate visitDate, LocalTime visitFrom,
			LocalTime visitTo, Instant submittedAt) {
	}

	// ---- Internal: requests ----

	public record PersonRef(long id, String fullName) {
	}

	public record VisitBrief(long id, VisitStatus status, Instant start, Instant end, PersonRef technician) {
	}

	public record ServiceRequestSummary(long id, String requestCode, RequestStatus status, RequestChannel channel,
			BranchSummary branch, String contactName, PersonRef customer, String deviceType, Province province,
			String canton, LocalDate preferredDate, PreferredWindow preferredWindow, Instant createdAt,
			VisitBrief activeVisit) {
	}

	public record RequestActions(boolean canStartReview, boolean canLinkCustomer, boolean canChangeBranch,
			boolean canSchedule, boolean canReject, boolean canCancel) {
	}

	public record EventView(EventType type, String fromStatus, String toStatus, Map<String, Object> details, String reason,
			PersonRef actor, Instant occurredAt, Long visitId) {
	}

	public record ServiceRequestDetail(long id, String requestCode, RequestStatus status, RequestChannel channel,
			BranchSummary branch, PersonRef customer, String contactName, String contactPhone, String contactEmail,
			Province province, String canton, String district, String addressLine, String deviceType, String brand,
			String model, String problemDescription, LocalDate preferredDate, PreferredWindow preferredWindow,
			String additionalNotes, boolean notificationsConsent, boolean emailConsent, boolean whatsappConsent,
			Instant contactConsentAt, String decisionReason, PersonRef createdBy, Instant createdAt, long version,
			List<VisitView> visits, List<EventView> history, List<NotificationStatus> notifications,
			RequestActions actions) {
	}

	/** Staff registers a request on the customer's behalf (phone call, counter). */
	public record StaffRequest(
			@NotNull UUID submissionId,
			@NotNull Long branchId,
			Long customerId,
			Boolean registerCustomer,
			Boolean confirmedNewPerson,
			@NotBlank @Size(max = 160) String contactName,
			@NotBlank @Size(max = 30) String contactPhone,
			@Email @Size(max = 254) String contactEmail,
			@NotNull Province province,
			@NotBlank @Size(max = 80) String canton,
			@Size(max = 80) String district,
			@NotBlank @Size(max = 300) String addressLine,
			@NotBlank @Size(max = 60) String deviceType,
			@Size(max = 60) String brand,
			@Size(max = 80) String model,
			@NotBlank @Size(max = 1000) String problemDescription,
			LocalDate preferredDate,
			PreferredWindow preferredWindow,
			@Size(max = 500) String additionalNotes,
			Boolean notificationsConsent) {
	}

	/**
	 * Link the request to a customer: an existing one ({@code customerId}; a customer of another
	 * branch only if its phone equals the request's) or a new record built from the contact data.
	 */
	public record LinkCustomerRequest(Long customerId, Boolean registerNew, Boolean confirmedNewPerson) {
	}

	public record ChangeBranchRequest(@NotNull Long branchId) {
	}

	public record DecisionRequest(@NotBlank @Size(max = 500) String reason) {
	}

	// ---- Internal: visits ----

	public record RepairOrderRef(long id, String orderCode) {
	}

	public record VisitActions(boolean canConfirm, boolean canReschedule, boolean canCancel, boolean canStart,
			boolean canComplete, boolean canLinkRepairOrder) {
	}

	/**
	 * A visit as the agenda and the technician see it. Contact phone and address are null for a
	 * technician outside an upcoming or running visit of theirs (ServicePolicy.mayViewContact).
	 */
	public record VisitView(long id, long requestId, String requestCode, VisitStatus status, BranchSummary branch,
			PersonRef technician, Instant start, Instant end, Instant blockedUntil, PersonRef customer,
			String contactName, String contactPhone, Province province, String canton, String district,
			String addressLine, String deviceType, String brand, String model, String problemDescription,
			Instant startedAt, Instant completedAt, VisitOutcome outcome, String outcomeNotes, String cancelReason,
			RepairOrderRef repairOrder, long version, VisitActions actions) {
	}

	/** Propose (tentative) or confirm directly. Duration defaults to the branch setting. */
	public record ScheduleVisitRequest(
			@NotNull UUID operationId,
			@NotNull Long technicianId,
			@NotNull Instant start,
			@Min(15) @Max(480) Integer durationMinutes,
			Boolean confirm) {
	}

	public record RescheduleVisitRequest(
			@NotNull Long technicianId,
			@NotNull Instant start,
			@Min(15) @Max(480) Integer durationMinutes,
			@Size(max = 500) String reason) {
	}

	public record CompleteVisitRequest(@NotNull VisitOutcome outcome, @Size(max = 1000) String notes) {
	}

	/** Link an existing order of the same customer, or open a new one from the visit's data. */
	public record LinkRepairOrderRequest(
			Long existingOrderId,
			UUID operationId,
			@Size(max = 1000) String physicalCondition,
			@Size(max = 500) String accessories) {
	}

	// ---- Scheduling configuration ----

	public record ShiftView(int dayOfWeek, BranchSummary branch, LocalTime start, LocalTime end, LocalTime breakStart,
			LocalTime breakEnd) {
	}

	public record ShiftInput(
			@NotNull @Min(1) @Max(7) Integer dayOfWeek,
			@NotNull Long branchId,
			@NotNull LocalTime start,
			@NotNull LocalTime end,
			LocalTime breakStart,
			LocalTime breakEnd) {
	}

	/** Replaces the technician's whole week: days not listed become days off. */
	public record WeekShiftsRequest(@NotNull @Size(max = 7) List<@Valid ShiftInput> shifts) {
	}

	public record TechnicianSchedule(PersonRef technician, List<ShiftView> shifts) {
	}

	public record ServiceSettings(long branchId, int defaultVisitMinutes, int bufferMinutes) {
	}

	// ---- Public portal settings (ADMIN) ----

	/** The portal as the administrator edits it, with the addresses it answered to before. */
	public record PortalSettingsView(boolean enabled, String slug, boolean allowPreferredDate,
			boolean allowPreferredWindow, int minNoticeDays, int maxDaysAhead, List<Integer> serviceDays,
			List<Province> servedProvinces, List<String> serviceTypes, String welcomeMessage, String successMessage,
			List<String> previousSlugs, Instant updatedAt, PersonRef updatedBy, long version) {
	}

	/** Everything but the address, which changes through its own confirmed request. Plain text only. */
	public record PortalSettingsRequest(
			@NotNull Boolean enabled,
			@NotNull Boolean allowPreferredDate,
			@NotNull Boolean allowPreferredWindow,
			@NotNull @Min(0) @Max(30) Integer minNoticeDays,
			@NotNull @Min(1) @Max(180) Integer maxDaysAhead,
			@NotNull @Size(min = 1, max = 7) List<@NotNull @Min(1) @Max(7) Integer> serviceDays,
			@NotNull @Size(min = 1, max = 7) List<@NotNull Province> servedProvinces,
			@NotNull @Size(max = 20) List<@NotBlank @Size(max = 60) String> serviceTypes,
			@Size(max = 300) String welcomeMessage,
			@Size(max = 500) String successMessage,
			@NotNull Long version) {
	}

	/** New address; the previous one keeps resolving to it. */
	public record PortalSlugRequest(@NotBlank @Size(max = 60) String slug, @NotNull Long version) {
	}

	public record ServiceSettingsRequest(
			@NotNull @Min(15) @Max(480) Integer defaultVisitMinutes,
			@NotNull @Min(0) @Max(240) Integer bufferMinutes) {
	}

	public record BusySlot(Instant start, Instant end, Instant blockedUntil, String requestCode) {
	}

	/** Free start times of one technician on one local day, for the scheduling form. */
	public record Availability(LocalDate date, PersonRef technician, ShiftView shift, int durationMinutes,
			int bufferMinutes, List<LocalTime> freeStarts, List<BusySlot> confirmed) {
	}

}
