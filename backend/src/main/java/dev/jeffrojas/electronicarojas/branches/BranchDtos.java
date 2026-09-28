package dev.jeffrojas.electronicarojas.branches;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HTTP contracts of the branches module. Entities never leave the service layer (ENG-031). */
public final class BranchDtos {

	private BranchDtos() {
	}

	public record BranchResponse(long id, String code, String name, String address, boolean active, long version) {

		static BranchResponse from(Branch branch) {
			return new BranchResponse(branch.getId(), branch.getCode(), branch.getName(), branch.getAddress(),
					branch.isActive(), branch.getVersion());
		}

	}

	/** Minimal view used by the branch selector and by other modules. */
	public record BranchSummary(long id, String code, String name) {

		public static BranchSummary from(Branch branch) {
			return new BranchSummary(branch.getId(), branch.getCode(), branch.getName());
		}

	}

	public record CreateBranchRequest(
			@NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9-]{1,19}",
					message = "must be 2-20 letters, digits or hyphens") String code,
			@NotBlank @Size(max = 120) String name,
			@Size(max = 300) String address) {
	}

	public record UpdateBranchRequest(
			@NotBlank @Size(max = 120) String name,
			@Size(max = 300) String address,
			@NotNull Boolean active,
			@NotNull Long version) {
	}

}
