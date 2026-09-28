package dev.jeffrojas.electronicarojas.inventory;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.CreateTransferRequest;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.MovementResponse;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.RecordMovementRequest;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.TransferResponse;
import dev.jeffrojas.electronicarojas.security.CurrentUser;

/**
 * Idempotent stock operations: 201 Created the first time, 200 OK with the same body when the
 * same operationId is replayed (ER-FS-001 section 7).
 */
@RestController
class StockOperationController {

	private final StockMovementService movementService;

	private final StockTransferService transferService;

	StockOperationController(StockMovementService movementService, StockTransferService transferService) {
		this.movementService = movementService;
		this.transferService = transferService;
	}

	@PostMapping("/api/v1/stock-movements")
	ResponseEntity<MovementResponse> record(@AuthenticationPrincipal CurrentUser user,
			@Valid @RequestBody RecordMovementRequest request) {
		OperationResult<MovementResponse> result = movementService.record(user, request);
		return respond(result, "/api/v1/branches/" + result.body().branch().id() + "/movements");
	}

	@PostMapping("/api/v1/stock-transfers")
	ResponseEntity<TransferResponse> transfer(@AuthenticationPrincipal CurrentUser user,
			@Valid @RequestBody CreateTransferRequest request) {
		OperationResult<TransferResponse> result = transferService.transfer(user, request);
		return respond(result, "/api/v1/stock-transfers/" + result.body().id());
	}

	@GetMapping("/api/v1/stock-transfers/{transferId}")
	TransferResponse transfer(@AuthenticationPrincipal CurrentUser user, @PathVariable long transferId) {
		return transferService.get(user, transferId);
	}

	private static <T> ResponseEntity<T> respond(OperationResult<T> result, String location) {
		if (result.replayed()) {
			return ResponseEntity.ok(result.body());
		}
		return ResponseEntity.created(URI.create(location)).body(result.body());
	}

}
