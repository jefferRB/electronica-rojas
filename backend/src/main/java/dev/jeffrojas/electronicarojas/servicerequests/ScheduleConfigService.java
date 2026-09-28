package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ServiceSettings;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ServiceSettingsRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ShiftInput;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.TechnicianSchedule;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.WeekShiftsRequest;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;
import dev.jeffrojas.electronicarojas.users.AppUser;
import dev.jeffrojas.electronicarojas.users.TechnicianDirectory;

/**
 * Working hours per technician and scheduling defaults per branch (BR-SRV-005). Simple explicit
 * rules: one shift per weekday, at one branch, with an optional break. Changing hours never moves
 * or cancels visits already confirmed; the agenda keeps showing them.
 */
@Service
@Transactional(readOnly = true)
public class ScheduleConfigService {

	private final TechnicianShiftRepository shifts;

	private final BranchServiceSettingsRepository settings;

	private final TechnicianDirectory technicianDirectory;

	private final BranchService branchService;

	private final VisitService visitService;

	private final AuditService audit;

	private final EntityManager entityManager;

	private final Clock clock;

	ScheduleConfigService(TechnicianShiftRepository shifts, BranchServiceSettingsRepository settings,
			TechnicianDirectory technicianDirectory, BranchService branchService, VisitService visitService,
			AuditService audit, EntityManager entityManager, Clock clock) {
		this.shifts = shifts;
		this.settings = settings;
		this.technicianDirectory = technicianDirectory;
		this.branchService = branchService;
		this.visitService = visitService;
		this.audit = audit;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	/** Technicians of a branch with their weekly hours (the configuration page and the agenda). */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public List<TechnicianSchedule> branchSchedules(CurrentUser user, long branchId) {
		branchService.requireReadable(user, branchId);
		return technicianDirectory.activeTechniciansOf(branchId)
			.stream()
			.map(technician -> new TechnicianSchedule(ServiceViews.person(technician),
					shifts.findForTechnician(technician.getId()).stream().map(VisitService::shiftView).toList()))
			.toList();
	}

	/**
	 * Replaces a technician's week. Each shift's branch must be one the technician belongs to and
	 * the caller operates; days the technician works at branches the caller does not operate are
	 * kept untouched (and cannot be overwritten from here).
	 */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
	@Transactional
	public TechnicianSchedule replaceWeek(CurrentUser user, long technicianId, WeekShiftsRequest week) {
		Set<Integer> days = new HashSet<>();
		for (ShiftInput shift : week.shifts()) {
			if (!days.add(shift.dayOfWeek())) {
				throw new InvalidRequestException("shifts", "DUPLICATE_DAY", "each weekday may appear once");
			}
		}
		AppUser technician = entityManager.find(AppUser.class, technicianId);
		if (technician == null) {
			throw new NotFoundException("Technician not found.");
		}
		Instant now = clock.instant();
		AppUser editor = entityManager.getReference(AppUser.class, user.id());
		Map<Integer, TechnicianShift> existing = shifts.findForTechnician(technicianId)
			.stream()
			.collect(Collectors.toMap(shift -> shift.getDayOfWeek().getValue(), shift -> shift));
		// Days at branches the caller does not operate are kept as they are.
		Set<Integer> foreignDays = existing.entrySet()
			.stream()
			.filter(entry -> !branchService.isOperable(user, entry.getValue().getBranch().getId()))
			.map(Map.Entry::getKey)
			.collect(Collectors.toSet());
		existing.keySet().removeAll(foreignDays);
		for (ShiftInput input : week.shifts()) {
			if (foreignDays.contains(input.dayOfWeek())) {
				throw new InvalidRequestException("shifts", "DAY_AT_OTHER_BRANCH",
						"that day is scheduled at a branch the caller does not manage");
			}
			Branch branch = requireOperable(user, input.branchId());
			if (technicianDirectory.eligibleTechnician(technicianId, branch.getId()).isEmpty()) {
				throw new InvalidRequestException("shifts", "TECHNICIAN_NOT_ELIGIBLE",
						"the technician is not an active technician of branch " + branch.getCode());
			}
			WorkingHours hours = new WorkingHours(input.start(), input.end(), input.breakStart(), input.breakEnd());
			TechnicianShift current = existing.remove(input.dayOfWeek());
			if (current == null) {
				shifts.save(new TechnicianShift(technician, DayOfWeek.of(input.dayOfWeek()), branch, hours, editor, now));
			}
			else {
				current.update(branch, hours, editor, now);
			}
		}
		// Days not listed become days off.
		shifts.deleteAll(existing.values());
		shifts.flush();
		audit.record(user, AuditEntry.of(AuditAction.TECHNICIAN_SCHEDULE_UPDATED, technicianId)
			.detail("technicianName", technician.getFullName())
			.detail("workingDays", week.shifts().size())
			.summary("Working hours of user " + technicianId + " replaced"));
		return new TechnicianSchedule(ServiceViews.person(technician),
				shifts.findForTechnician(technicianId).stream().map(VisitService::shiftView).toList());
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public ServiceSettings settings(CurrentUser user, long branchId) {
		branchService.requireReadable(user, branchId);
		BranchServiceSettings current = visitService.settingsFor(branchId);
		return new ServiceSettings(branchId, current.getDefaultVisitMinutes(), current.getBufferMinutes());
	}

	/** New values apply to visits scheduled from now on; existing visits keep their times. */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
	@Transactional
	public ServiceSettings updateSettings(CurrentUser user, long branchId, ServiceSettingsRequest request) {
		Branch branch = branchService.requireOperable(user, branchId);
		AppUser editor = entityManager.getReference(AppUser.class, user.id());
		Instant now = clock.instant();
		BranchServiceSettings current = settings.findById(branchId).orElse(null);
		if (current == null) {
			current = settings.save(new BranchServiceSettings(branchId, request.defaultVisitMinutes(),
					request.bufferMinutes(), editor, now));
		}
		else {
			current.update(request.defaultVisitMinutes(), request.bufferMinutes(), editor, now);
		}
		audit.record(user, AuditEntry.of(AuditAction.SERVICE_SETTINGS_UPDATED, branchId)
			.branch(branchId)
			.detail("branchCode", branch.getCode())
			.detail("branchName", branch.getName())
			.detail("defaultVisitMinutes", request.defaultVisitMinutes())
			.detail("bufferMinutes", request.bufferMinutes())
			.summary("Service settings of " + branch.getCode() + " updated"));
		return new ServiceSettings(branchId, current.getDefaultVisitMinutes(), current.getBufferMinutes());
	}

	private Branch requireOperable(CurrentUser user, long branchId) {
		try {
			return branchService.requireOperable(user, branchId);
		}
		catch (NotFoundException ex) {
			throw new AccessDeniedException("Branch " + branchId + " is not managed by the caller");
		}
	}

}
