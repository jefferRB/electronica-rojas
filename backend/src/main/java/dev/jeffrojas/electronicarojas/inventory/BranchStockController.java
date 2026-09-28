package dev.jeffrojas.electronicarojas.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.MovementResponse;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.StockItemResponse;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.UpdateMinimumRequest;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;

/** Stock and history of one branch. The branch id in the URL is always re-authorized server side. */
@RestController
@RequestMapping("/api/v1/branches/{branchId}")
class BranchStockController {

	private final BranchStockService stockService;

	BranchStockController(BranchStockService stockService) {
		this.stockService = stockService;
	}

	@GetMapping("/stock")
	PageResponse<StockItemResponse> stock(@AuthenticationPrincipal CurrentUser user, @PathVariable long branchId,
			@RequestParam(required = false) String search, @RequestParam(required = false) String category,
			@RequestParam(required = false) ProductKind kind, @RequestParam(defaultValue = "ACTIVE") StatusFilter status,
			@RequestParam(required = false) StockStatusFilter stockStatus,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(ProductCatalogService.MAX_PAGE_SIZE) int size) {
		return stockService.list(user, branchId, search, category, kind, status.active(), stockStatus, page, size);
	}

	@PutMapping("/stock/{productId}/minimum")
	StockItemResponse changeMinimum(@AuthenticationPrincipal CurrentUser user, @PathVariable long branchId,
			@PathVariable long productId, @Valid @RequestBody UpdateMinimumRequest request) {
		return stockService.changeMinimum(user, branchId, productId, request.minimumQuantity());
	}

	@GetMapping("/movements")
	PageResponse<MovementResponse> movements(@AuthenticationPrincipal CurrentUser user, @PathVariable long branchId,
			@RequestParam(required = false) Long productId, @RequestParam(required = false) MovementType type,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(ProductCatalogService.MAX_PAGE_SIZE) int size) {
		return stockService.history(user, branchId, productId, type, page, size);
	}

}
