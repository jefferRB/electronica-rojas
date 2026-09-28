package dev.jeffrojas.electronicarojas.users;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.security.Role;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;
import dev.jeffrojas.electronicarojas.users.UserDtos.CreateUserRequest;
import dev.jeffrojas.electronicarojas.users.UserDtos.ResetPasswordRequest;
import dev.jeffrojas.electronicarojas.users.UserDtos.UpdateUserRequest;
import dev.jeffrojas.electronicarojas.users.UserDtos.UserResponse;

/**
 * Account administration (FR-AUTH-004, FR-BRH-002, BR-BRH-003/004). Every public method
 * requires ADMIN; the check runs in the Spring proxy before the method body.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public class UserAdminService {

	static final int MAX_PAGE_SIZE = 100;

	private static final String NOT_FOUND = "User not found.";

	private final AppUserRepository users;

	private final BranchService branchService;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	private final AuditService audit;

	UserAdminService(AppUserRepository users, BranchService branchService, PasswordEncoder passwordEncoder,
			Clock clock, AuditService audit) {
		this.users = users;
		this.branchService = branchService;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public PageResponse<UserResponse> list(int page, int size) {
		// Type-safe property references: renaming a getter breaks compilation, not the query.
		Sort.TypedSort<AppUser> byUser = Sort.sort(AppUser.class);
		Sort order = byUser.by(AppUser::getEmail).ascending().and(byUser.by(AppUser::getId).ascending());
		PageRequest request = PageRequest.of(page, size, order);
		return PageResponse.of(users.findAll(request), UserResponse::from);
	}

	@Transactional(readOnly = true)
	public UserResponse get(long userId) {
		return UserResponse.from(require(userId));
	}

	public UserResponse create(CreateUserRequest request) {
		String email = AppUser.normalizeEmail(request.email());
		if (users.existsByEmail(email)) {
			throw new ConflictException("DUPLICATE_EMAIL", "An account with this email already exists.");
		}
		PasswordPolicy.validate("password", request.password());
		List<Branch> branches = resolveAssignments(request.role(), request.branchIds());

		Instant now = clock.instant();
		AppUser user = new AppUser(email, request.fullName(), passwordEncoder.encode(request.password()), request.role(),
				now);
		user.assignBranches(branches, now);
		users.saveAndFlush(user);
		audit.record(CurrentUser.fromSecurityContext(), AuditEntry.of(AuditAction.USER_CREATED, user.getId())
			.detail("userName", user.getFullName())
			.detail("role", user.getRole())
			.detail("branchCount", user.getBranches().size())
			.summary("Account created with role " + user.getRole() + " and " + user.getBranches().size()
					+ " branch(es)"));
		return UserResponse.from(user);
	}

	public UserResponse update(long userId, UpdateUserRequest request) {
		AppUser user = require(userId);
		requireVersion(user, request.version());
		List<Branch> branches = resolveAssignments(request.role(), request.branchIds());
		boolean losesAdmin = user.isActive() && user.getRole() == Role.ADMIN
				&& (request.role() != Role.ADMIN || !request.active());
		if (losesAdmin) {
			ensureAnotherActiveAdminExists();
		}

		Instant now = clock.instant();
		user.rename(request.fullName(), now);
		user.changeAccess(request.role(), request.active(), now);
		user.assignBranches(branches, now);
		users.saveAndFlush(user);
		audit.record(CurrentUser.fromSecurityContext(), AuditEntry.of(AuditAction.USER_UPDATED, user.getId())
			.detail("userName", user.getFullName())
			.detail("role", user.getRole())
			.detail("active", user.isActive())
			.detail("branchCount", user.getBranches().size())
			.summary("Account updated: role " + user.getRole() + ", active=" + user.isActive() + ", "
					+ user.getBranches().size() + " branch(es)"));
		return UserResponse.from(user);
	}

	/** Controlled access reset: open sessions of the account are revoked on their next request. */
	public void resetPassword(long userId, ResetPasswordRequest request) {
		AppUser user = require(userId);
		requireVersion(user, request.version());
		PasswordPolicy.validate("newPassword", request.newPassword());
		user.changePasswordHash(passwordEncoder.encode(request.newPassword()), clock.instant());
		users.saveAndFlush(user);
		// Never include the password or its hash in the trail.
		audit.record(CurrentUser.fromSecurityContext(), AuditEntry.of(AuditAction.USER_PASSWORD_RESET, user.getId())
			.detail("userName", user.getFullName())
			.summary("Password reset by an administrator"));
	}

	private AppUser require(long userId) {
		return users.findById(userId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
	}

	private static void requireVersion(AppUser user, long expectedVersion) {
		if (user.getVersion() != expectedVersion) {
			throw new ConflictException("STALE_VERSION", "The user was modified by someone else. Reload and try again.");
		}
	}

	/** Non-admin roles need at least one active branch (BR-BRH-002); admins need none. */
	private List<Branch> resolveAssignments(Role role, List<Long> branchIds) {
		if (role == Role.ADMIN) {
			return List.of();
		}
		Set<Long> requested = new HashSet<>(branchIds);
		if (requested.isEmpty()) {
			throw new InvalidRequestException("branchIds", "BRANCHES_REQUIRED", "at least one branch is required for this role");
		}
		List<Branch> found = branchService.findActiveByIds(requested);
		if (found.size() != requested.size()) {
			throw new InvalidRequestException("branchIds", "BRANCHES_INVALID", "contains unknown or inactive branches");
		}
		return found;
	}

	/** BR-BRH-004: the last active administrator cannot be demoted or deactivated. */
	private void ensureAnotherActiveAdminExists() {
		if (users.lockActiveByRole(Role.ADMIN).size() <= 1) {
			throw new ConflictException("LAST_ADMIN", "At least one active administrator is required.");
		}
	}

}
