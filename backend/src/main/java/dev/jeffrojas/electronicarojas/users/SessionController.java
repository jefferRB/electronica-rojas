package dev.jeffrojas.electronicarojas.users;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.security.Role;

/**
 * Current session for the SPA: who is logged in and which branches the selector may offer.
 * Anonymous callers get 401 from the security chain before reaching this controller.
 */
@RestController
class SessionController {

	private final BranchService branchService;

	SessionController(BranchService branchService) {
		this.branchService = branchService;
	}

	@GetMapping("/api/v1/auth/me")
	SessionResponse me(@AuthenticationPrincipal CurrentUser user) {
		return new SessionResponse(new SessionUser(user.id(), user.email(), user.fullName(), user.role()),
				branchService.listSelectable(user));
	}

	/** Single source for the UI's password hints (A.6). */
	@GetMapping("/api/v1/auth/password-policy")
	PasswordPolicy.Description passwordPolicy() {
		return PasswordPolicy.describe();
	}

	record SessionUser(long id, String email, String fullName, Role role) {
	}

	record SessionResponse(SessionUser user, List<BranchSummary> branches) {
	}

}
