package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.Availability;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.CompleteVisitRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.DecisionRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.LinkRepairOrderRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PersonRef;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.RescheduleVisitRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.VisitView;

@RestController
@RequestMapping("/api/v1/service-visits")
class ServiceVisitController {

	private final VisitService visitService;

	ServiceVisitController(VisitService visitService) {
		this.visitService = visitService;
	}

	/** Agenda: visits overlapping [from, to), scoped to the caller. */
	@GetMapping
	List<VisitView> agenda(@AuthenticationPrincipal CurrentUser user, @RequestParam Instant from, @RequestParam Instant to,
			@RequestParam(required = false) Long branchId, @RequestParam(required = false) Long technicianId) {
		return visitService.agenda(user, from, to, branchId, technicianId);
	}

	@GetMapping("/availability")
	Availability availability(@AuthenticationPrincipal CurrentUser user, @RequestParam long technicianId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) @Min(15) @Max(480) Integer durationMinutes) {
		return visitService.availability(user, technicianId, date, durationMinutes);
	}

	@GetMapping("/technicians")
	List<PersonRef> technicians(@AuthenticationPrincipal CurrentUser user, @RequestParam long branchId) {
		return visitService.technicians(user, branchId);
	}

	@GetMapping("/by-repair-order/{repairOrderId}")
	VisitView byRepairOrder(@AuthenticationPrincipal CurrentUser user, @PathVariable long repairOrderId) {
		return visitService.forRepairOrder(user, repairOrderId);
	}

	@GetMapping("/{visitId}")
	VisitView get(@AuthenticationPrincipal CurrentUser user, @PathVariable long visitId) {
		return visitService.get(user, visitId);
	}

	@PostMapping("/{visitId}/confirm")
	VisitView confirm(@AuthenticationPrincipal CurrentUser user, @PathVariable long visitId) {
		return visitService.confirm(user, visitId);
	}

	@PutMapping("/{visitId}/schedule")
	VisitView reschedule(@AuthenticationPrincipal CurrentUser user, @PathVariable long visitId,
			@Valid @RequestBody RescheduleVisitRequest request) {
		return visitService.reschedule(user, visitId, request);
	}

	@PostMapping("/{visitId}/cancel")
	VisitView cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable long visitId,
			@Valid @RequestBody DecisionRequest request) {
		return visitService.cancel(user, visitId, request);
	}

	@PostMapping("/{visitId}/start")
	VisitView start(@AuthenticationPrincipal CurrentUser user, @PathVariable long visitId) {
		return visitService.start(user, visitId);
	}

	@PostMapping("/{visitId}/complete")
	VisitView complete(@AuthenticationPrincipal CurrentUser user, @PathVariable long visitId,
			@Valid @RequestBody CompleteVisitRequest request) {
		return visitService.complete(user, visitId, request);
	}

	@PostMapping("/{visitId}/repair-order")
	VisitView linkRepairOrder(@AuthenticationPrincipal CurrentUser user, @PathVariable long visitId,
			@Valid @RequestBody LinkRepairOrderRequest request) {
		return visitService.linkRepairOrder(user, visitId, request);
	}

}
