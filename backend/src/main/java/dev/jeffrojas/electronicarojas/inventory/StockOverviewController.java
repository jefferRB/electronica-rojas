package dev.jeffrojas.electronicarojas.inventory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.StockOverviewResponse;
import dev.jeffrojas.electronicarojas.security.CurrentUser;

/** Consolidated stock of all branches in the caller's scope. There is no branch id to tamper with. */
@RestController
class StockOverviewController {

	private final BranchStockService stockService;

	StockOverviewController(BranchStockService stockService) {
		this.stockService = stockService;
	}

	@GetMapping("/api/v1/stock/overview")
	StockOverviewResponse overview(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) String search, @RequestParam(required = false) String category,
			@RequestParam(required = false) ProductKind kind, @RequestParam(defaultValue = "ACTIVE") StatusFilter status,
			@RequestParam(required = false) StockStatusFilter stockStatus,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(ProductCatalogService.MAX_PAGE_SIZE) int size) {
		return stockService.overview(user, search, category, kind, status.active(), stockStatus, page, size);
	}

}
