package dev.jeffrojas.electronicarojas.users;

import java.util.Comparator;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.security.Role;

/** HTTP contracts of the users module. Never exposes password hashes (ER-FS-001 section 12). */
public final class UserDtos {

	private UserDtos() {
	}

	public record UserResponse(long id, String email, String fullName, Role role, boolean active,
			List<BranchSummary> branches, long version) {

		static UserResponse from(AppUser user) {
			List<BranchSummary> branches = user.getBranches()
				.stream()
				.map(BranchSummary::from)
				.sorted(Comparator.comparing(BranchSummary::code))
				.toList();
			return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isActive(),
					branches, user.getVersion());
		}

	}

	public record CreateUserRequest(
			@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(max = 120) String fullName,
			@NotNull Role role,
			@NotNull String password,
			@NotNull List<@NotNull Long> branchIds) {

		@Override
		public String toString() {
			return "CreateUserRequest[email=" + email + ", role=" + role + ", password=***]";
		}

	}

	public record UpdateUserRequest(
			@NotBlank @Size(max = 120) String fullName,
			@NotNull Role role,
			@NotNull Boolean active,
			@NotNull List<@NotNull Long> branchIds,
			@NotNull Long version) {
	}

	public record ResetPasswordRequest(@NotNull String newPassword, @NotNull Long version) {

		@Override
		public String toString() {
			return "ResetPasswordRequest[newPassword=***, version=" + version + "]";
		}

	}

}
