package dev.jeffrojas.electronicarojas.servicerequests;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.CreateRepairOrderRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.RepairOrderDetail;
import dev.jeffrojas.electronicarojas.repairs.RepairOrder;
import dev.jeffrojas.electronicarojas.repairs.RepairOrderService;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.security.Role;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.Availability;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.BusySlot;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.CompleteVisitRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.DecisionRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.LinkRepairOrderRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PersonRef;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.RescheduleVisitRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ScheduleVisitRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ShiftView;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.VisitView;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.EventType;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitOutcome;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceVisit.Slot;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;
import dev.jeffrojas.electronicarojas.users.AppUser;
import dev.jeffrojas.electronicarojas.users.TechnicianDirectory;

/**
 * Visits and the technicians' agenda (FR-SRV-003/004, BR-SRV-003..008).
 * <p>
 * Double-booking is prevented in two layers: while checking and writing a technician's
 * confirmed time the transaction holds an advisory lock on that technician, so concurrent
 * confirmations are serialized and the second one sees the first (clear 409); and the V9
 * exclusion constraint makes an overlapping CONFIRMED / IN_PROGRESS pair impossible to store.
 */
@Service
@Transactional(readOnly = true)
public class VisitService {

	/** Widest agenda window per request (a month and a bit, for the week view across months). */
	static final int MAX_AGENDA_DAYS = 42;

	private static final List<Long> NO_BRANCHES = List.of(-1L);

	private final ServiceVisitRepository visits;

	private final TechnicianShiftRepository shifts;

	private final BranchServiceSettingsRepository settings;

	private final ServiceRequestAccess access;

	private final ServiceRequestService requestService;

	private final ServiceViews views;

	private final ServiceTimeline timeline;

	private final TechnicianDirectory technicianDirectory;

	private final BranchService branchService;

	private final RepairOrderService repairOrderService;

	private final EntityManager entityManager;

	private final Clock clock;

	VisitService(ServiceVisitRepository visits, TechnicianShiftRepository shifts, BranchServiceSettingsRepository settings,
			ServiceRequestAccess access, ServiceRequestService requestService, ServiceViews views, ServiceTimeline timeline,
			TechnicianDirectory technicianDirectory, BranchService branchService, RepairOrderService repairOrderService,
			EntityManager entityManager, Clock clock) {
		this.visits = visits;
		this.shifts = shifts;
		this.settings = settings;
		this.access = access;
		this.requestService = requestService;
		this.views = views;
		this.timeline = timeline;
		this.technicianDirectory = technicianDirectory;
		this.branchService = branchService;
		this.repairOrderService = repairOrderService;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@PreAuthorize("isAuthenticated()")
	public VisitView get(CurrentUser user, long visitId) {
		return views.visit(user, access.requireVisit(user, visitId));
	}

	/**
	 * Proposes (tentative) or confirms a visit for a request. Requires a linked customer and no
	 * other active visit; the technician must work that day at the request's branch and be free.
	 * Idempotent by {@code operationId}.
	 */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	@Transactional
	public VisitView schedule(CurrentUser user, long requestId, ScheduleVisitRequest command) {
		Optional<ServiceVisit> replay = visits.findByOperationId(command.operationId());
		if (replay.isPresent()) {
			ServiceVisit existing = replay.get();
			if (existing.getRequest().getId() != requestId || !existing.getTechnician().getId().equals(command.technicianId())) {
				throw new ConflictException("OPERATION_ID_REUSED", "This operationId was already used for a different visit.");
			}
			return views.visit(user, access.requireVisit(user, existing.getId()));
		}
		ServiceRequest request = access.requireRequestForUpdate(user, requestId);
		ServiceRequestService.requireOpen(request);
		if (request.getCustomer() == null) {
			throw new ConflictException("CUSTOMER_NOT_LINKED", "Link the request to a customer before scheduling a visit.");
		}
		List<ServiceVisit> requestVisits = visits.findForRequest(request.getId());
		if (requestVisits.stream().anyMatch(ServiceVisit::isActive)) {
			throw new ConflictException("VISIT_ALREADY_ACTIVE", "The request already has a scheduled visit; reschedule it instead.");
		}
		if (requestVisits.stream().anyMatch(v -> v.getStatus() == VisitStatus.COMPLETED)) {
			throw new ConflictException("REQUEST_COMPLETED", "The request was already attended.");
		}
		AppUser technician = eligibleTechnician(command.technicianId(), request);
		Slot slot = slot(request, command.start(), command.durationMinutes());
		validateSlot(request, technician, slot, null);

		boolean confirm = Boolean.TRUE.equals(command.confirm());
		VisitStatus status = confirm ? VisitStatus.CONFIRMED : VisitStatus.PROPOSED;
		ServiceVisit visit = saveChecked(new ServiceVisit(request, command.operationId(), technician, status, slot,
				entityManager.getReference(AppUser.class, user.id()), clock.instant()));
		requestService.move(user, request, RequestStatus.UNDER_REVIEW, null);
		if (confirm) {
			requestService.move(user, request, RequestStatus.ACCEPTED, null);
		}
		timeline.record(user, request, visit, confirm ? EventType.VISIT_CONFIRMED : EventType.VISIT_PROPOSED, null, status,
				slotDetails(slot, technician, null, null), null);
		return views.visit(user, visit);
	}

	/** The customer agreed to the proposed time: re-validated now, under the technician's lock. */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	@Transactional
	public VisitView confirm(CurrentUser user, long visitId) {
		ServiceVisit visit = access.requireVisitForUpdate(user, visitId);
		if (visit.getStatus() == VisitStatus.CONFIRMED) {
			return views.visit(user, visit);
		}
		requireTransition(visit, VisitStatus.CONFIRMED);
		ServiceRequest request = visit.getRequest();
		validateSlot(request, visit.getTechnician(), visit.slot(), visit.getId());
		visit.moveTo(VisitStatus.CONFIRMED, clock.instant());
		flushChecked();
		requestService.move(user, request, RequestStatus.ACCEPTED, null);
		timeline.record(user, request, visit, EventType.VISIT_CONFIRMED, VisitStatus.PROPOSED, VisitStatus.CONFIRMED,
				slotDetails(visit.slot(), visit.getTechnician(), null, null), null);
		return views.visit(user, visit);
	}

	/** New time and/or technician for a visit that has not started; the old values stay in the timeline. */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	@Transactional
	public VisitView reschedule(CurrentUser user, long visitId, RescheduleVisitRequest command) {
		ServiceVisit visit = access.requireVisitForUpdate(user, visitId);
		if (!ServicePolicy.canReschedule(visit.getStatus())) {
			throw new ConflictException("VISIT_NOT_RESCHEDULABLE", "A visit can only be moved before it starts.",
					Map.of("status", visit.getStatus().name()));
		}
		ServiceRequest request = visit.getRequest();
		AppUser technician = eligibleTechnician(command.technicianId(), request);
		Slot slot = slot(request, command.start(), command.durationMinutes());
		Slot previous = visit.slot();
		AppUser previousTechnician = visit.getTechnician();
		if (previous.equals(slot) && previousTechnician.getId().equals(technician.getId())) {
			return views.visit(user, visit);
		}
		validateSlot(request, technician, slot, visit.getId());
		visit.reschedule(technician, slot, clock.instant());
		flushChecked();
		String reason = command.reason() == null || command.reason().isBlank() ? null : command.reason().strip();
		timeline.record(user, request, visit, EventType.VISIT_RESCHEDULED, visit.getStatus(), visit.getStatus(),
				slotDetails(slot, technician, previous, previousTechnician), reason);
		return views.visit(user, visit);
	}

	/** Cancels a visit that has not started and frees the technician; the request goes back to review. */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	@Transactional
	public VisitView cancel(CurrentUser user, long visitId, DecisionRequest decision) {
		ServiceVisit visit = access.requireVisitForUpdate(user, visitId);
		requireTransition(visit, VisitStatus.CANCELLED);
		VisitStatus from = visit.getStatus();
		String reason = decision.reason().strip();
		visit.cancel(reason, clock.instant());
		ServiceRequest request = visit.getRequest();
		timeline.record(user, request, visit, EventType.VISIT_CANCELLED, from, VisitStatus.CANCELLED, null, reason);
		if (request.getStatus() == RequestStatus.ACCEPTED) {
			requestService.move(user, request, RequestStatus.UNDER_REVIEW, null);
		}
		return views.visit(user, visit);
	}

	/** The technician arrived. Not before the visit's day (Costa Rica). */
	@PreAuthorize("isAuthenticated()")
	@Transactional
	public VisitView start(CurrentUser user, long visitId) {
		ServiceVisit visit = access.requireVisitForUpdate(user, visitId);
		requireWorker(user, visit);
		if (visit.getStatus() == VisitStatus.IN_PROGRESS) {
			return views.visit(user, visit);
		}
		requireTransition(visit, VisitStatus.IN_PROGRESS);
		Instant now = clock.instant();
		if (ScheduleRules.localDate(now).isBefore(ScheduleRules.localDate(visit.getScheduledStart()))) {
			throw new ConflictException("VISIT_NOT_TODAY", "The visit is scheduled for a later day.");
		}
		visit.moveTo(VisitStatus.IN_PROGRESS, now);
		timeline.record(user, visit.getRequest(), visit, EventType.VISIT_STARTED, VisitStatus.CONFIRMED,
				VisitStatus.IN_PROGRESS, null, null);
		return views.visit(user, visit);
	}

	@PreAuthorize("isAuthenticated()")
	@Transactional
	public VisitView complete(CurrentUser user, long visitId, CompleteVisitRequest command) {
		ServiceVisit visit = access.requireVisitForUpdate(user, visitId);
		requireWorker(user, visit);
		requireTransition(visit, VisitStatus.COMPLETED);
		visit.complete(command.outcome(), command.notes(), clock.instant());
		timeline.record(user, visit.getRequest(), visit, EventType.VISIT_COMPLETED, VisitStatus.IN_PROGRESS,
				VisitStatus.COMPLETED, Map.of("outcome", command.outcome().name()), null);
		return views.visit(user, visit);
	}

	/**
	 * The appliance goes to the workshop: link an existing order of the same customer, or open one
	 * from the visit's data (same transaction, same customer - never a duplicate).
	 */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	@Transactional
	public VisitView linkRepairOrder(CurrentUser user, long visitId, LinkRepairOrderRequest command) {
		ServiceVisit visit = access.requireVisitForUpdate(user, visitId);
		if (visit.getStatus() != VisitStatus.COMPLETED || visit.getOutcome() != VisitOutcome.NEEDS_WORKSHOP) {
			throw new ConflictException("REPAIR_LINK_NOT_ALLOWED",
					"Only a completed visit whose outcome is 'needs workshop' can be linked to a repair order.");
		}
		if (visit.getRepairOrder() != null) {
			if (command.existingOrderId() != null && command.existingOrderId().equals(visit.getRepairOrder().getId())) {
				return views.visit(user, visit);
			}
			throw new ConflictException("VISIT_ALREADY_LINKED", "The visit is already linked to a repair order.");
		}
		ServiceRequest request = visit.getRequest();
		long orderId;
		if (command.existingOrderId() != null) {
			RepairOrderDetail order = repairOrderService.get(user, command.existingOrderId());
			if (order.customer().id() != request.getCustomer().getId()) {
				throw new ConflictException("ORDER_CUSTOMER_MISMATCH", "The repair order belongs to another customer.");
			}
			visits.findByRepairOrderId(order.id()).ifPresent(other -> {
				throw new ConflictException("ORDER_ALREADY_LINKED", "The repair order is already linked to another visit.");
			});
			orderId = order.id();
		}
		else {
			if (command.operationId() == null) {
				throw new InvalidRequestException("operationId", "NotNull", "is required to open a new order");
			}
			if (command.physicalCondition() == null || command.physicalCondition().isBlank()) {
				throw new InvalidRequestException("physicalCondition", "NotBlank", "describe the appliance's condition");
			}
			String fault = request.getProblemDescription()
					+ (visit.getOutcomeNotes() == null ? "" : "\nVisita a domicilio: " + visit.getOutcomeNotes());
			orderId = repairOrderService.receive(user, new CreateRepairOrderRequest(command.operationId(),
					request.getBranch().getId(), request.getCustomer().getId(), request.getContactPhone(), null,
					request.getDeviceType(), request.getBrand() == null ? "Sin especificar" : request.getBrand(),
					request.getModel(), null, fault.length() > 1000 ? fault.substring(0, 1000) : fault,
					command.physicalCondition(), command.accessories())).id();
		}
		RepairOrder order = entityManager.getReference(RepairOrder.class, orderId);
		visit.linkRepairOrder(order, clock.instant());
		flushChecked();
		timeline.record(user, request, visit, EventType.REPAIR_ORDER_LINKED, null, null,
				Map.of("orderCode", order.getOrderCode()), null);
		return views.visit(user, visit);
	}

	/**
	 * Agenda between two instants. Staff: visits of their branches; technicians: only their own.
	 * Cancelled visits are left out; proposed ones are included and flagged by status.
	 */
	@PreAuthorize("isAuthenticated()")
	public List<VisitView> agenda(CurrentUser user, Instant from, Instant to, Long branchId, Long technicianId) {
		if (!to.isAfter(from) || Duration.between(from, to).toDays() > MAX_AGENDA_DAYS) {
			throw new InvalidRequestException("to", "DATE_RANGE_INVALID", "the range must be positive and at most " + MAX_AGENDA_DAYS + " days");
		}
		boolean restrict = !user.isAdmin();
		List<Long> branchIds = restrict ? branchService.listSelectable(user).stream().map(BranchSummary::id).toList()
				: NO_BRANCHES;
		Long onlyTechnician = user.role() == Role.TECHNICIAN ? user.id() : null;
		return visits.agenda(restrict, branchIds.isEmpty() ? NO_BRANCHES : branchIds, onlyTechnician, branchId, technicianId,
				from, to)
			.stream()
			.map(visit -> views.visit(user, visit))
			.toList();
	}

	/** Free start times of one technician on one local day, at the branch of that day's shift. */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public Availability availability(CurrentUser user, long technicianId, LocalDate date, Integer durationMinutes) {
		TechnicianShift shift = shifts.findDay(technicianId, (short) date.getDayOfWeek().getValue()).orElse(null);
		AppUser technician = entityManager.find(AppUser.class, technicianId);
		if (technician == null || technician.getRole() != Role.TECHNICIAN) {
			throw new NotFoundException("Technician not found.");
		}
		if (shift == null) {
			return new Availability(date, ServiceViews.person(technician), null, 0, 0, List.of(), List.of());
		}
		branchService.requireReadable(user, shift.getBranch().getId());
		BranchServiceSettings branchSettings = settingsFor(shift.getBranch().getId());
		int duration = durationMinutes == null ? branchSettings.getDefaultVisitMinutes() : durationMinutes;
		Instant dayStart = date.atStartOfDay(ScheduleRules.ZONE).toInstant();
		Instant dayEnd = date.plusDays(1).atStartOfDay(ScheduleRules.ZONE).toInstant();
		List<ServiceVisit> confirmed = visits.findBlocking(technicianId, dayStart.minus(Duration.ofHours(4)), dayEnd, null);
		List<LocalTime> free = ScheduleRules.freeStarts(date, shift.hours(),
				confirmed.stream().map(v -> new ScheduleRules.Busy(v.getScheduledStart(), v.getBlockedUntil())).toList(),
				Duration.ofMinutes(duration), Duration.ofMinutes(branchSettings.getBufferMinutes()), clock.instant());
		return new Availability(date, ServiceViews.person(technician), shiftView(shift), duration,
				branchSettings.getBufferMinutes(), free,
				confirmed.stream()
					.map(v -> new BusySlot(v.getScheduledStart(), v.getScheduledEnd(), v.getBlockedUntil(),
							v.getRequest().getRequestCode()))
					.toList());
	}

	/** Technicians who may take visits of a branch (active, TECHNICIAN, assigned to it). */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public List<PersonRef> technicians(CurrentUser user, long branchId) {
		branchService.requireReadable(user, branchId);
		return technicianDirectory.activeTechniciansOf(branchId).stream().map(ServiceViews::person).toList();
	}

	/** The visit an order came from, for the order's detail page (404 when none or not visible). */
	@PreAuthorize("isAuthenticated()")
	public VisitView forRepairOrder(CurrentUser user, long repairOrderId) {
		ServiceVisit visit = visits.findByRepairOrderId(repairOrderId)
			.filter(candidate -> access.canSeeVisit(user, candidate))
			.orElseThrow(() -> new NotFoundException("Visit not found."));
		return views.visit(user, visit);
	}

	// ---- rules ----

	BranchServiceSettings settingsFor(long branchId) {
		return settings.findById(branchId)
			.orElseGet(() -> new BranchServiceSettings(branchId, BranchServiceSettings.DEFAULT_VISIT_MINUTES,
					BranchServiceSettings.DEFAULT_BUFFER_MINUTES, null, clock.instant()));
	}

	static ShiftView shiftView(TechnicianShift shift) {
		return new ShiftView(shift.getDayOfWeek().getValue(), BranchSummary.from(shift.getBranch()), shift.getStartTime(),
				shift.getEndTime(), shift.getBreakStart(), shift.getBreakEnd());
	}

	private Slot slot(ServiceRequest request, Instant start, Integer durationMinutes) {
		BranchServiceSettings branchSettings = settingsFor(request.getBranch().getId());
		int duration = durationMinutes == null ? branchSettings.getDefaultVisitMinutes() : durationMinutes;
		Instant end = start.plus(Duration.ofMinutes(duration));
		return new Slot(start, end, end.plus(Duration.ofMinutes(branchSettings.getBufferMinutes())));
	}

	/**
	 * The slot is in the future, inside the technician's shift of that day at the request's branch,
	 * outside the break, and free of CONFIRMED / IN_PROGRESS visits (checked under the
	 * technician's advisory lock, held until commit).
	 */
	private void validateSlot(ServiceRequest request, AppUser technician, Slot slot, Long excludeVisitId) {
		if (!slot.start().isAfter(clock.instant())) {
			throw new InvalidRequestException("start", "VISIT_IN_PAST", "the visit must start in the future");
		}
		LocalDate day = ScheduleRules.localDate(slot.start());
		TechnicianShift shift = shifts.findDay(technician.getId(), (short) day.getDayOfWeek().getValue())
			.filter(candidate -> candidate.getBranch().getId().equals(request.getBranch().getId()))
			.orElseThrow(() -> new ConflictException("NO_SHIFT",
					"The technician does not work that day at this branch.", Map.of("date", day.toString())));
		String violation = ScheduleRules.shiftViolation(slot.start(), slot.end(), shift.hours());
		if (violation != null) {
			Map<String, Object> properties = new LinkedHashMap<>();
			properties.put("shiftStart", shift.getStartTime().toString());
			properties.put("shiftEnd", shift.getEndTime().toString());
			if (shift.getBreakStart() != null) {
				properties.put("breakStart", shift.getBreakStart().toString());
				properties.put("breakEnd", shift.getBreakEnd().toString());
			}
			throw new ConflictException(violation, "The visit is outside the technician's working hours.", properties);
		}
		visits.lockTechnicianSchedule(technician.getId());
		List<ServiceVisit> conflicts = visits.findBlocking(technician.getId(), slot.start(), slot.blockedUntil(),
				excludeVisitId);
		if (!conflicts.isEmpty()) {
			ServiceVisit other = conflicts.get(0);
			throw new ConflictException("SCHEDULE_CONFLICT", "The technician already has a confirmed visit at that time.",
					Map.of("conflictStart", other.getScheduledStart().toString(), "conflictEnd",
							other.getScheduledEnd().toString(), "conflictBlockedUntil", other.getBlockedUntil().toString(),
							"conflictRequestCode", other.getRequest().getRequestCode()));
		}
	}

	private AppUser eligibleTechnician(long technicianId, ServiceRequest request) {
		return technicianDirectory.eligibleTechnician(technicianId, request.getBranch().getId())
			.orElseThrow(() -> new InvalidRequestException("technicianId", "TECHNICIAN_NOT_ELIGIBLE",
					"must be an active technician assigned to the request's branch"));
	}

	private static void requireTransition(ServiceVisit visit, VisitStatus target) {
		if (!ServicePolicy.canMove(visit.getStatus(), target)) {
			throw ServiceRequestService.invalidTransition(visit.getStatus(), target);
		}
	}

	private static void requireWorker(CurrentUser user, ServiceVisit visit) {
		if (!ServicePolicy.mayWorkVisit(user, visit)) {
			throw new AccessDeniedException("Only the assigned technician or management can work a visit");
		}
	}

	private ServiceVisit saveChecked(ServiceVisit visit) {
		try {
			return visits.saveAndFlush(visit);
		}
		catch (DataIntegrityViolationException ex) {
			throw translate(ex);
		}
	}

	private void flushChecked() {
		try {
			visits.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw translate(ex);
		}
	}

	/** 23P01 = exclusion_violation: the database refused an overlapping confirmed visit. */
	private static RuntimeException translate(DataIntegrityViolationException ex) {
		if (ex.getMostSpecificCause() instanceof SQLException sql && "23P01".equals(sql.getSQLState())) {
			return new ConflictException("SCHEDULE_CONFLICT", "The technician already has a confirmed visit at that time.");
		}
		return ex;
	}

	private static Map<String, Object> slotDetails(Slot slot, AppUser technician, Slot previous, AppUser previousTechnician) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("start", slot.start().toString());
		details.put("end", slot.end().toString());
		details.put("technicianName", technician.getFullName());
		if (previous != null) {
			details.put("previousStart", previous.start().toString());
			details.put("previousEnd", previous.end().toString());
			details.put("previousTechnicianName", previousTechnician.getFullName());
		}
		return details;
	}

}
