package dev.jeffrojas.electronicarojas.repairs;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.NewCustomer;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.ProductSummary;
import dev.jeffrojas.electronicarojas.notifications.NotificationDtos.NotificationStatus;
import dev.jeffrojas.electronicarojas.repairs.RepairEnums.DecisionMethod;
import dev.jeffrojas.electronicarojas.repairs.RepairEnums.QuoteDecision;
import dev.jeffrojas.electronicarojas.repairs.RepairEnums.QuoteStatus;
import dev.jeffrojas.electronicarojas.repairs.RepairEnums.Resolution;
import dev.jeffrojas.electronicarojas.users.AppUser;

/** HTTP contracts of the repairs module. Entities never leave the service layer (ENG-031). */
public final class RepairDtos {

	private RepairDtos() {
	}

	public record PersonSummary(long id, String fullName) {

		static PersonSummary of(AppUser user) {
			return user == null ? null : new PersonSummary(user.getId(), user.getFullName());
		}

	}

	/** Customer as shown inside an order. Contact data is null for technicians (data minimization). */
	public record OrderCustomer(long id, String fullName, String phone, String email) {
	}

	public record Device(String type, String brand, String model, String serialNumber) {
	}

	/** List row (FR-REP-002). */
	public record RepairOrderSummary(long id, String orderCode, RepairStatus status, Resolution resolution,
			boolean inCustody, BranchSummary branch, PersonSummary customer, Device device, PersonSummary technician,
			Instant receivedAt) {

		static RepairOrderSummary from(RepairOrder order) {
			return new RepairOrderSummary(order.getId(), order.getOrderCode(), order.getStatus(), order.getResolution(),
					order.isInCustody(), BranchSummary.from(order.getBranch()),
					new PersonSummary(order.getCustomer().getId(), order.getCustomer().getFullName()), RepairDtos.device(order),
					PersonSummary.of(order.getAssignedTechnician()), order.getReceivedAt());
		}

	}

	public record StatusChange(RepairStatus fromStatus, RepairStatus toStatus, String reason, PersonSummary actor,
			Instant changedAt) {
	}

	public record Quote(long id, BigDecimal amount, String currency, String description, QuoteStatus status,
			PersonSummary createdBy, Instant createdAt, PersonSummary decidedBy, Instant decidedAt,
			DecisionMethod decisionMethod, String decisionNote) {
	}

	/** A manual status change the caller may request now (computed by RepairPolicy). */
	public record AvailableTransition(RepairStatus toStatus, boolean reasonRequired) {
	}

	/** What the caller may do with this order right now; the server enforces the same rules. */
	public record OrderActions(List<AvailableTransition> transitions, boolean canAssignTechnician,
			boolean canEditDiagnosis, boolean canCreateQuote, boolean canDecideQuote, boolean canConsumeParts,
			boolean canReturnParts, boolean canOverridePartPricing) {
	}

	/** A correction of a consumed part: units back to the shelf, why, and the order status then. */
	public record PartReturn(long id, int quantity, String reason, RepairStatus orderStatus, PersonSummary recordedBy,
			Instant recordedAt) {
	}

	/**
	 * A spare part used in the order (BR-REP-011), with what was returned and what remains used, and
	 * its pricing snapshot (BR-REP-014). {@code chargedAmount} = price x units still in use when the
	 * line is charged, 0 when it is not, null when it is charged but has no price yet.
	 * {@code unitCost} is null for roles that may not see costs.
	 */
	public record PartUsage(long id, ProductSummary product, BranchSummary branch, int quantity, int returnedQuantity,
			int remainingQuantity, BigDecimal unitPrice, BigDecimal unitCost, boolean chargeable,
			boolean priceOverridden, BigDecimal chargedAmount, String note, PersonSummary recordedBy,
			Instant recordedAt, List<PartReturn> returns) {
	}

	/**
	 * Totals of the parts still in use. Shown next to the quotes, never added to them: a quote is an
	 * amount approved as a whole. {@code totalCost} is null for roles that may not see costs.
	 */
	public record PartsSummary(int linesInUse, int unitsInUse, int unitsWithoutCharge, BigDecimal chargeableSubtotal,
			int unpricedLines, BigDecimal totalCost, int uncostedLines, String currency) {
	}

	public record RepairOrderDetail(long id, String orderCode, RepairStatus status, Resolution resolution,
			boolean inCustody, BranchSummary branch, OrderCustomer customer, Device device, String reportedFault,
			String physicalCondition, String accessories, PersonSummary technician, String diagnosis,
			Instant diagnosisUpdatedAt, PersonSummary diagnosisUpdatedBy, Instant receivedAt, PersonSummary receivedBy,
			Instant deliveredAt, PersonSummary deliveredBy, long version, List<StatusChange> history,
			List<Quote> quotes, List<PartUsage> parts, PartsSummary partsSummary, List<NotificationStatus> notifications,
			OrderActions actions) {
	}

	static Device device(RepairOrder order) {
		return new Device(order.getDeviceType(), order.getBrand(), order.getModel(), order.getSerialNumber());
	}

	// ---- Requests ----

	/**
	 * Reception (FR-REP-001). Exactly one of {@code customerId} or {@code newCustomer}. For a customer
	 * outside the caller's scope, {@code customerPhone} must match the phone on file.
	 * {@code operationId} makes a double submit create a single order.
	 */
	public record CreateRepairOrderRequest(
			@NotNull UUID operationId,
			@NotNull Long branchId,
			Long customerId,
			@Size(max = 30) String customerPhone,
			@Valid NewCustomer newCustomer,
			@NotBlank @Size(max = 60) String deviceType,
			@NotBlank @Size(max = 60) String brand,
			@Size(max = 80) String model,
			@Size(max = 80) String serialNumber,
			@NotBlank @Size(max = 1000) String reportedFault,
			@NotBlank @Size(max = 1000) String physicalCondition,
			@Size(max = 500) String accessories) {
	}

	public record TransitionRequest(@NotNull RepairStatus toStatus, @Size(max = 500) String reason) {
	}

	public record AssignTechnicianRequest(@NotNull Long technicianId) {
	}

	public record DiagnosisRequest(@NotBlank @Size(max = 2000) String diagnosis, @NotNull Long version) {
	}

	public record CreateQuoteRequest(
			@NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
			@NotBlank @Size(max = 1000) String description) {
	}

	/** Explicit decision + how the customer communicated it; nothing is approved by default. */
	public record QuoteDecisionRequest(@NotNull QuoteDecision decision, @NotNull DecisionMethod method,
			@Size(max = 500) String note) {
	}

	public record TechnicianOption(long id, String fullName) {
	}

	/**
	 * Records a spare part used in the order; {@code operationId} makes a retry apply once.
	 * {@code unitPrice} and {@code chargeable} are optional: null takes the catalog's sale price and
	 * default. Changing either for this line is a management decision (ADMIN, BRANCH_MANAGER) and
	 * never changes the catalog.
	 */
	public record ConsumePartRequest(
			@NotNull UUID operationId,
			@NotNull Long productId,
			@NotNull @Min(1) @Max(MAX_PART_QUANTITY) Integer quantity,
			@Size(max = 300) String note,
			@PositiveOrZero @Digits(integer = InventoryDtos.MONEY_INTEGER_DIGITS, fraction = 2) BigDecimal unitPrice,
			Boolean chargeable) {
	}

	/** Gives back units of a consumed part (partial or complete); the reason is mandatory. */
	public record ReturnPartRequest(
			@NotNull UUID operationId,
			@NotNull @Min(1) @Max(MAX_PART_QUANTITY) Integer quantity,
			@NotBlank @Size(max = 300) String reason) {
	}

	/** Upper bound of one part line (mirrored by a CHECK in V10). */
	public static final int MAX_PART_QUANTITY = 1000;

}
