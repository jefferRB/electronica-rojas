package dev.jeffrojas.electronicarojas.users;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jeffrojas.electronicarojas.shared.web.PageResponse;
import dev.jeffrojas.electronicarojas.users.UserDtos.CreateUserRequest;
import dev.jeffrojas.electronicarojas.users.UserDtos.ResetPasswordRequest;
import dev.jeffrojas.electronicarojas.users.UserDtos.UpdateUserRequest;
import dev.jeffrojas.electronicarojas.users.UserDtos.UserResponse;

/** Administration of collaborator accounts. Authorization is enforced in UserAdminService. */
@RestController
@RequestMapping("/api/v1/users")
class UserController {

	private final UserAdminService userAdminService;

	UserController(UserAdminService userAdminService) {
		this.userAdminService = userAdminService;
	}

	@GetMapping
	PageResponse<UserResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(UserAdminService.MAX_PAGE_SIZE) int size) {
		return userAdminService.list(page, size);
	}

	@GetMapping("/{userId}")
	UserResponse get(@PathVariable long userId) {
		return userAdminService.get(userId);
	}

	@PostMapping
	ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
		UserResponse created = userAdminService.create(request);
		return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
	}

	@PutMapping("/{userId}")
	UserResponse update(@PathVariable long userId, @Valid @RequestBody UpdateUserRequest request) {
		return userAdminService.update(userId, request);
	}

	@PostMapping("/{userId}/password-reset")
	ResponseEntity<Void> resetPassword(@PathVariable long userId, @Valid @RequestBody ResetPasswordRequest request) {
		userAdminService.resetPassword(userId, request);
		return ResponseEntity.noContent().build();
	}

}
