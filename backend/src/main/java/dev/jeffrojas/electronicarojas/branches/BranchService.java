package dev.jeffrojas.electronicarojas.branches;

import java.time.Clock;
import java.util.Collection;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchResponse;
import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.branches.BranchDtos.CreateBranchRequest;
import dev.jeffrojas.electronicarojas.branches.BranchDtos.UpdateBranchRequest;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;

/**
 * Branch use cases and the single source of truth for branch scope (ENG-015, ARCH-SEC-005):
 * ADMIN sees every branch; any other role sees only ACTIVE branches assigned to it, re-read
 * from the database on each call. Out-of-scope and non-existent branches both produce the same
 * 404, so IDs cannot be probed.
 */
@Service
@Transactional(readOnly = true)
public class BranchService {

	private static final String NOT_FOUND = "Branch not found.";

	private final BranchRepository branches;

	private final Clock clock;

	private final AuditService audit;

	BranchService(BranchRepository branches, Clock clock, AuditService audit) {
		this.branches = branches;
		this.clock = clock;
		this.audit = audit;
	}

	/** Branches the user may view. Admins also see inactive ones (for administration). */
	public List<BranchResponse> listVisible(CurrentUser user) {
		List<Branch> visible = user.isAdmin() ? branches.findAllByOrderByCodeAsc()
				: branches.findActiveAssignedTo(user.id());
		return visible.stream().map(BranchResponse::from).toList();
	}

	/** Active branches the user can work in: the options of the branch selector. */
	public List<BranchSummary> listSelectable(CurrentUser user) {
		List<Branch> selectable = user.isAdmin() ? branches.findByActiveTrueOrderByCodeAsc()
				: branches.findActiveAssignedTo(user.id());
		return selectable.stream().map(BranchSummary::from).toList();
	}

	public BranchResponse get(CurrentUser user, long branchId) {
		return BranchResponse.from(requireReadable(user, branchId));
	}

	/**
	 * Authorization entry point for READ use cases of other modules (stock, history): admins may
	 * read any branch, including deactivated ones (history stays queryable); others only their
	 * active assigned branches. Missing and out-of-scope branches give the same 404.
	 */
	public Branch requireReadable(CurrentUser user, long branchId) {
		if (user.isAdmin()) {
			return branches.findById(branchId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
		}
		return branches.findActiveAssigned(user.id(), branchId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
	}

	/**
	 * Same rule as {@link #requireReadable} as a yes/no answer, for visibility checks that must
	 * not throw: an exception escaping a transactional method marks the caller's transaction
	 * rollback-only even when the caller catches it.
	 */
	public boolean isReadable(CurrentUser user, long branchId) {
		return user.isAdmin() ? branches.existsById(branchId) : branches.findActiveAssigned(user.id(), branchId).isPresent();
	}

	/** Same rule as {@link #requireOperable} as a yes/no answer (see {@link #isReadable}). */
	public boolean isOperable(CurrentUser user, long branchId) {
		return user.isAdmin() ? branches.findById(branchId).filter(Branch::isActive).isPresent()
				: branches.findActiveAssigned(user.id(), branchId).isPresent();
	}

	/**
	 * Authorization entry point for WRITE use cases of other modules (inventory, repairs...):
	 * returns the branch only if it is active and the user may operate in it, otherwise throws the
	 * same 404 as a missing branch.
	 */
	public Branch requireOperable(CurrentUser user, long branchId) {
		if (user.isAdmin()) {
			return branches.findById(branchId).filter(Branch::isActive).orElseThrow(() -> new NotFoundException(NOT_FOUND));
		}
		return branches.findActiveAssigned(user.id(), branchId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
	}

	/** Active branches for the public service form (anonymous): the caller exposes names only. */
	public List<Branch> activeBranches() {
		return branches.findByActiveTrueOrderByCodeAsc();
	}

	/** Loads active branches for assignment; the caller decides how to report missing IDs. */
	public List<Branch> findActiveByIds(Collection<Long> ids) {
		return branches.findByIdIn(ids).stream().filter(Branch::isActive).toList();
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public BranchResponse create(CreateBranchRequest request) {
		String code = Branch.normalizeCode(request.code());
		if (branches.existsByCode(code)) {
			throw new ConflictException("DUPLICATE_BRANCH_CODE", "A branch with code " + code + " already exists.");
		}
		Branch branch = branches.saveAndFlush(new Branch(code, request.name(), request.address(), clock.instant()));
		audit.record(CurrentUser.fromSecurityContext(), AuditEntry.of(AuditAction.BRANCH_CREATED, branch.getId())
			.branch(branch.getId())
			.detail("branchCode", branch.getCode())
			.detail("branchName", branch.getName())
			.summary("Branch " + branch.getCode() + " created"));
		return BranchResponse.from(branch);
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public BranchResponse update(long branchId, UpdateBranchRequest request) {
		Branch branch = branches.findById(branchId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
		if (branch.getVersion() != request.version()) {
			throw new ConflictException("STALE_VERSION", "The branch was modified by someone else. Reload and try again.");
		}
		branch.update(request.name(), request.address(), request.active(), clock.instant());
		// Flush so the response carries the incremented @Version.
		branches.saveAndFlush(branch);
		audit.record(CurrentUser.fromSecurityContext(), AuditEntry.of(AuditAction.BRANCH_UPDATED, branch.getId())
			.branch(branch.getId())
			.detail("branchCode", branch.getCode())
			.detail("branchName", branch.getName())
			.detail("active", branch.isActive())
			.summary("Branch " + branch.getCode() + " updated (active=" + branch.isActive() + ")"));
		return BranchResponse.from(branch);
	}

}
