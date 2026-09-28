package dev.jeffrojas.electronicarojas.servicerequests;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ServiceSettings;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ServiceSettingsRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.TechnicianSchedule;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.WeekShiftsRequest;

@RestController
class ScheduleConfigController {

	private final ScheduleConfigService service;

	ScheduleConfigController(ScheduleConfigService service) {
		this.service = service;
	}

	@GetMapping("/api/v1/technician-schedules")
	List<TechnicianSchedule> branchSchedules(@AuthenticationPrincipal CurrentUser user, @RequestParam long branchId) {
		return service.branchSchedules(user, branchId);
	}

	@PutMapping("/api/v1/technician-schedules/{technicianId}")
	TechnicianSchedule replaceWeek(@AuthenticationPrincipal CurrentUser user, @PathVariable long technicianId,
			@Valid @RequestBody WeekShiftsRequest week) {
		return service.replaceWeek(user, technicianId, week);
	}

	@GetMapping("/api/v1/branches/{branchId}/service-settings")
	ServiceSettings settings(@AuthenticationPrincipal CurrentUser user, @PathVariable long branchId) {
		return service.settings(user, branchId);
	}

	@PutMapping("/api/v1/branches/{branchId}/service-settings")
	ServiceSettings updateSettings(@AuthenticationPrincipal CurrentUser user, @PathVariable long branchId,
			@Valid @RequestBody ServiceSettingsRequest request) {
		return service.updateSettings(user, branchId, request);
	}

}
