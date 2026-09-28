package dev.jeffrojas.electronicarojas.branches;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchResponse;
import dev.jeffrojas.electronicarojas.branches.BranchDtos.CreateBranchRequest;
import dev.jeffrojas.electronicarojas.branches.BranchDtos.UpdateBranchRequest;
import dev.jeffrojas.electronicarojas.security.CurrentUser;

/**
 * Thin HTTP adapter (ENG-013). The authenticated user always comes from the server-side session
 * ({@code @AuthenticationPrincipal}), never from the request body or URL.
 */
@RestController
@RequestMapping("/api/v1/branches")
class BranchController {

	private final BranchService branchService;

	BranchController(BranchService branchService) {
		this.branchService = branchService;
	}

	/** Small, bounded list (one company's branches), so it is not paginated. */
	@GetMapping
	List<BranchResponse> list(@AuthenticationPrincipal CurrentUser user) {
		return branchService.listVisible(user);
	}

	@GetMapping("/{branchId}")
	BranchResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable long branchId) {
		return branchService.get(user, branchId);
	}

	@PostMapping
	ResponseEntity<BranchResponse> create(@Valid @RequestBody CreateBranchRequest request) {
		BranchResponse created = branchService.create(request);
		return ResponseEntity.created(URI.create("/api/v1/branches/" + created.id())).body(created);
	}

	@PutMapping("/{branchId}")
	BranchResponse update(@PathVariable long branchId, @Valid @RequestBody UpdateBranchRequest request) {
		return branchService.update(branchId, request);
	}

}
