package dev.jeffrojas.electronicarojas.inventory;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.CreateProductRequest;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.ProductDetailResponse;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.ProductResponse;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.UpdateProductRequest;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/products")
class ProductController {

	private final ProductCatalogService catalog;

	ProductController(ProductCatalogService catalog) {
		this.catalog = catalog;
	}

	/** {@code status}: ACTIVE (default), INACTIVE or ALL. */
	@GetMapping
	PageResponse<ProductResponse> search(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String category, @RequestParam(required = false) ProductKind kind,
			@RequestParam(defaultValue = "ACTIVE") StatusFilter status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(ProductCatalogService.MAX_PAGE_SIZE) int size) {
		return catalog.search(user, search, category, kind, status.active(), page, size);
	}

	@GetMapping("/categories")
	List<String> categories() {
		return catalog.categories();
	}

	@GetMapping("/{productId}")
	ProductDetailResponse detail(@AuthenticationPrincipal CurrentUser user, @PathVariable long productId) {
		return catalog.detail(user, productId);
	}

	@PostMapping
	ResponseEntity<ProductResponse> create(@AuthenticationPrincipal CurrentUser user,
			@Valid @RequestBody CreateProductRequest request) {
		ProductResponse created = catalog.create(user, request);
		return ResponseEntity.created(URI.create("/api/v1/products/" + created.id())).body(created);
	}

	@PutMapping("/{productId}")
	ProductResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable long productId,
			@Valid @RequestBody UpdateProductRequest request) {
		return catalog.update(user, productId, request);
	}

}
