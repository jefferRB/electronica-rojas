package dev.jeffrojas.electronicarojas.repairs;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.customers.Customer;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.NewCustomer;
import dev.jeffrojas.electronicarojas.customers.CustomerService;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.AssignTechnicianRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.CreateRepairOrderRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.DiagnosisRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.RepairOrderDetail;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.RepairOrderSummary;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.TechnicianOption;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.TransitionRequest;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.security.Role;
import dev.jeffrojas.electronicarojas.shared.ClockConfig;
import dev.jeffrojas.electronicarojas.shared.OperationFingerprint;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;
import dev.jeffrojas.electronicarojas.users.AppUser;
import dev.jeffrojas.electronicarojas.users.TechnicianDirectory;

/**
 * Repair order use cases (FR-REP-001..005): reception, list, detail, status changes, technician
 * assignment and diagnosis. Every change locks the order row first, so concurrent requests on the
 * same order are serialized; each use case is one transaction (order + history + audit together).
 */
@Service
@Transactional(readOnly = true)
public class RepairOrderService {

	static final int MAX_PAGE_SIZE = 100;

	/** Never matches a real branch; keeps {@code IN (...)} valid for a user with no branches. */
	private static final List<Long> NO_BRANCHES = List.of(-1L);

	private final RepairOrderRepository orders;

	private final RepairOrderAccess access;

	private final RepairWorkflow workflow;

	private final RepairDetailAssembler assembler;

	private final BranchService branchService;

	private final CustomerService customerService;

	private final TechnicianDirectory technicianDirectory;

	private final AuditService audit;

	private final EntityManager entityManager;

	private final Clock clock;

	RepairOrderService(RepairOrderRepository orders, RepairOrderAccess access, RepairWorkflow workflow,
			RepairDetailAssembler assembler, BranchService branchService, CustomerService customerService,
			TechnicianDirectory technicianDirectory, AuditService audit, EntityManager entityManager, Clock clock) {
		this.orders = orders;
		this.access = access;
		this.workflow = workflow;
		this.assembler = assembler;
		this.branchService = branchService;
		this.customerService = customerService;
		this.technicianDirectory = technicianDirectory;
		this.audit = audit;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	/** Filters of the order list; all optional. Dates are calendar days in Costa Rica, inclusive. */
	public record OrderFilter(Long branchId, RepairStatus status, Long technicianId, Long customerId, String search,
			LocalDate from, LocalDate to) {
	}

	@PreAuthorize("isAuthenticated()")
	public PageResponse<RepairOrderSummary> list(CurrentUser user, OrderFilter filter, int page, int size) {
		if (filter.from() != null && filter.to() != null && filter.to().isBefore(filter.from())) {
			throw new InvalidRequestException("to", "DATE_RANGE_INVALID", "must not be before 'from'");
		}
		boolean restrict = !user.isAdmin();
		List<Long> branchIds = restrict
				? branchService.listSelectable(user).stream().map(BranchSummary::id).toList()
				: NO_BRANCHES;
		if (branchIds.isEmpty()) {
			branchIds = NO_BRANCHES;
		}
		Long onlyTechnician = user.role() == Role.TECHNICIAN ? user.id() : null;
		Instant from = filter.from() == null ? null
				: filter.from().atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
		Instant to = filter.to() == null ? null
				: filter.to().plusDays(1).atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
		return PageResponse.of(orders.search(restrict, branchIds, onlyTechnician, filter.branchId(), filter.status(),
				filter.technicianId(), filter.customerId(), likePattern(filter.search()), from, to,
				PageRequest.of(page, size)), RepairOrderSummary::from);
	}

	@PreAuthorize("isAuthenticated()")
	public RepairOrderDetail get(CurrentUser user, long orderId) {
		return assembler.detail(user, access.requireVisible(user, orderId));
	}

	/**
	 * Reception (FR-REP-001): customer (existing or new) + appliance + initial history + audit, all or
	 * nothing. Idempotent by {@code operationId}: a double submit returns the same order.
	 */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	@Transactional
	public RepairOrderDetail receive(CurrentUser user, CreateRepairOrderRequest request) {
		String fingerprint = intakeFingerprint(request);
		UUID operationId = request.operationId();
		orders.lockIntake(operationId.getMostSignificantBits() ^ operationId.getLeastSignificantBits());
		Optional<RepairOrder> replay = orders.findByIntakeOperationId(request.operationId());
		if (replay.isPresent()) {
			RepairOrder existing = replay.get();
			if (!existing.getIntakeFingerprint().equals(fingerprint) || existing.getReceivedBy().getId() != user.id()) {
				throw new ConflictException("OPERATION_ID_REUSED",
						"This operationId was already used for a different operation.");
			}
			return assembler.detail(user, access.requireVisible(user, existing.getId()));
		}

		Branch branch = branchService.requireOperable(user, request.branchId());
		Customer customer = resolveCustomer(user, branch, request);
		Instant now = clock.instant();
		String code = "OR-" + now.atZone(ClockConfig.BUSINESS_ZONE).getYear() + "-"
				+ String.format(Locale.ROOT, "%06d", orders.nextOrderNumber());
		RepairOrder order = orders.saveAndFlush(new RepairOrder(code, request.operationId(), fingerprint, customer, branch,
				new RepairOrder.Intake(request.deviceType(), request.brand(), request.model(), request.serialNumber(),
						request.reportedFault(), request.physicalCondition(), request.accessories()),
				entityManager.getReference(AppUser.class, user.id()), now));
		workflow.recordReception(user, order);
		audit.record(user, AuditEntry.of(AuditAction.REPAIR_ORDER_RECEIVED, order.getId())
			.branch(branch.getId())
			.operation(request.operationId())
			.detail("orderCode", code)
			.detail("deviceType", order.getDeviceType())
			.detail("brand", order.getBrand())
			.detail("branchCode", branch.getCode())
			.detail("branchName", branch.getName())
			.summary("Order " + code + " received at " + branch.getCode()));
		return assembler.detail(user, order);
	}

	/** Manual status change through the generic endpoint (quote-driven changes are refused). */
	@PreAuthorize("isAuthenticated()")
	@Transactional
	public RepairOrderDetail transition(CurrentUser user, long orderId, TransitionRequest request) {
		RepairOrder order = access.requireVisibleForUpdate(user, orderId);
		workflow.change(user, order, request.toStatus(), request.reason(), false);
		return assembler.detail(user, order);
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
	@Transactional
	public RepairOrderDetail assignTechnician(CurrentUser user, long orderId, AssignTechnicianRequest request) {
		RepairOrder order = access.requireVisibleForUpdate(user, orderId);
		if (!RepairPolicy.mayAssignTechnician(user, order)) {
			throw orderClosed(order);
		}
		AppUser technician = technicianDirectory.eligibleTechnician(request.technicianId(), order.getBranch().getId())
			.orElseThrow(() -> new InvalidRequestException("technicianId", "TECHNICIAN_NOT_ELIGIBLE",
					"must be an active technician assigned to the order's branch"));
		AppUser previous = order.getAssignedTechnician();
		if (previous != null && previous.getId().equals(technician.getId())) {
			return assembler.detail(user, order);
		}
		order.assignTechnician(technician, clock.instant());
		orders.saveAndFlush(order);
		audit.record(user, AuditEntry.of(AuditAction.REPAIR_TECHNICIAN_ASSIGNED, order.getId())
			.branch(order.getBranch().getId())
			.detail("orderCode", order.getOrderCode())
			.detail("technicianName", technician.getFullName())
			.detail("previousTechnicianName", previous == null ? null : previous.getFullName())
			.summary("Order " + order.getOrderCode() + " assigned to user " + technician.getId()));
		return assembler.detail(user, order);
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'TECHNICIAN')")
	@Transactional
	public RepairOrderDetail updateDiagnosis(CurrentUser user, long orderId, DiagnosisRequest request) {
		RepairOrder order = access.requireVisibleForUpdate(user, orderId);
		if (order.getVersion() != request.version()) {
			throw new ConflictException("STALE_VERSION", "The order was modified by someone else. Reload and try again.");
		}
		if (!RepairPolicy.mayEditDiagnosis(user, order)) {
			throw orderClosed(order);
		}
		order.recordDiagnosis(request.diagnosis(), entityManager.getReference(AppUser.class, user.id()), clock.instant());
		orders.saveAndFlush(order);
		// The diagnosis text stays on the order; the audit only says that it changed.
		audit.record(user, AuditEntry.of(AuditAction.REPAIR_DIAGNOSIS_UPDATED, order.getId())
			.branch(order.getBranch().getId())
			.detail("orderCode", order.getOrderCode())
			.summary("Diagnosis of order " + order.getOrderCode() + " updated"));
		return assembler.detail(user, order);
	}

	/** Options for the assignment form: active technicians of a branch the caller can manage. */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
	public List<TechnicianOption> technicians(CurrentUser user, long branchId) {
		Branch branch = branchService.requireReadable(user, branchId);
		return technicianDirectory.activeTechniciansOf(branch.getId())
			.stream()
			.map(technician -> new TechnicianOption(technician.getId(), technician.getFullName()))
			.toList();
	}

	private Customer resolveCustomer(CurrentUser user, Branch branch, CreateRepairOrderRequest request) {
		boolean existing = request.customerId() != null;
		boolean inline = request.newCustomer() != null;
		if (existing == inline) {
			throw new InvalidRequestException("customerId", "CUSTOMER_REQUIRED",
					"give exactly one of customerId or newCustomer");
		}
		return existing ? customerService.requireForIntake(user, request.customerId(), request.customerPhone())
				: customerService.register(user, branch, request.newCustomer());
	}

	private static String intakeFingerprint(CreateRepairOrderRequest request) {
		NewCustomer inline = request.newCustomer();
		return OperationFingerprint.of("REPAIR_INTAKE", request.branchId(), request.customerId(),
				inline == null ? null : inline.fullName(), inline == null ? null : inline.phone(), request.deviceType(),
				request.brand(), request.model(), request.serialNumber(), request.reportedFault(),
				request.physicalCondition(), request.accessories());
	}

	private static ConflictException orderClosed(RepairOrder order) {
		return new ConflictException("ORDER_CLOSED", "This change is not possible in status " + order.getStatus() + ".",
				Map.of("status", order.getStatus().name()));
	}

	private static String likePattern(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		return "%" + text.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
			.replace("_", "\\_") + "%";
	}

}
