package dev.jeffrojas.electronicarojas.dashboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jeffrojas.electronicarojas.dashboard.DashboardDtos.DashboardResponse;
import dev.jeffrojas.electronicarojas.security.CurrentUser;

@RestController
class DashboardController {

	private final DashboardService dashboard;

	DashboardController(DashboardService dashboard) {
		this.dashboard = dashboard;
	}

	/** {@code branchId} absent: all the user's branches (not for receptionists). */
	@GetMapping("/api/v1/dashboard")
	DashboardResponse dashboard(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) Long branchId) {
		return dashboard.dashboard(user, branchId);
	}

}
