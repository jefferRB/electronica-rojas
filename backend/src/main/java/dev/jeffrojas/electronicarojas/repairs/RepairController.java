package dev.jeffrojas.electronicarojas.repairs;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.SparePartOption;
import dev.jeffrojas.electronicarojas.inventory.OperationResult;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.AssignTechnicianRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.ConsumePartRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.CreateQuoteRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.CreateRepairOrderRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.DiagnosisRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.QuoteDecisionRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.RepairOrderDetail;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.RepairOrderSummary;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.ReturnPartRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.TechnicianOption;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.TransitionRequest;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;

/** HTTP adapter of the repairs module; every rule lives in the services (ENG-013). */
@RestController
@RequestMapping("/api/v1/repair-orders")
class RepairController {

	private final RepairOrderService orderService;

	private final RepairQuoteService quoteService;

	private final RepairPartService partService;

	RepairController(RepairOrderService orderService, RepairQuoteService quoteService, RepairPartService partService) {
		this.orderService = orderService;
		this.quoteService = quoteService;
		this.partService = partService;
	}

	@GetMapping
	PageResponse<RepairOrderSummary> list(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) Long branchId, @RequestParam(required = false) RepairStatus status,
			@RequestParam(required = false) Long technicianId, @RequestParam(required = false) Long customerId,
			@RequestParam(required = false) @Size(max = 100) String search,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(RepairOrderService.MAX_PAGE_SIZE) int size) {
		return orderService.list(user, new RepairOrderService.OrderFilter(branchId, status, technicianId, customerId,
				search, from, to), page, size);
	}

	/** Declared before /{orderId} for readability; the path segments do not overlap anyway. */
	@GetMapping("/technicians")
	List<TechnicianOption> technicians(@AuthenticationPrincipal CurrentUser user, @RequestParam long branchId) {
		return orderService.technicians(user, branchId);
	}

	@GetMapping("/{orderId}")
	RepairOrderDetail get(@AuthenticationPrincipal CurrentUser user, @PathVariable long orderId) {
		return orderService.get(user, orderId);
	}

	@PostMapping
	ResponseEntity<RepairOrderDetail> receive(@AuthenticationPrincipal CurrentUser user,
			@Valid @RequestBody CreateRepairOrderRequest request) {
		RepairOrderDetail created = orderService.receive(user, request);
		return ResponseEntity.created(URI.create("/api/v1/repair-orders/" + created.id())).body(created);
	}

	@PostMapping("/{orderId}/status")
	RepairOrderDetail transition(@AuthenticationPrincipal CurrentUser user, @PathVariable long orderId,
			@Valid @RequestBody TransitionRequest request) {
		return orderService.transition(user, orderId, request);
	}

	@PutMapping("/{orderId}/technician")
	RepairOrderDetail assignTechnician(@AuthenticationPrincipal CurrentUser user, @PathVariable long orderId,
			@Valid @RequestBody AssignTechnicianRequest request) {
		return orderService.assignTechnician(user, orderId, request);
	}

	@PutMapping("/{orderId}/diagnosis")
	RepairOrderDetail updateDiagnosis(@AuthenticationPrincipal CurrentUser user, @PathVariable long orderId,
			@Valid @RequestBody DiagnosisRequest request) {
		return orderService.updateDiagnosis(user, orderId, request);
	}

	@PostMapping("/{orderId}/quotes")
	RepairOrderDetail createQuote(@AuthenticationPrincipal CurrentUser user, @PathVariable long orderId,
			@Valid @RequestBody CreateQuoteRequest request) {
		return quoteService.create(user, orderId, request);
	}

	@PostMapping("/{orderId}/quotes/{quoteId}/decision")
	RepairOrderDetail decideQuote(@AuthenticationPrincipal CurrentUser user, @PathVariable long orderId,
			@PathVariable long quoteId, @Valid @RequestBody QuoteDecisionRequest request) {
		return quoteService.decide(user, orderId, quoteId, request);
	}

	/** Spare parts of the order's branch with stock, for the consumption form. */
	@GetMapping("/{orderId}/parts/options")
	PageResponse<SparePartOption> partOptions(@AuthenticationPrincipal CurrentUser user, @PathVariable long orderId,
			@RequestParam(required = false) @Size(max = 100) String search,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "10") @Min(1) @Max(RepairPartService.MAX_PAGE_SIZE) int size) {
		return partService.options(user, orderId, search, page, size);
	}

	/** 201 when the part is recorded, 200 with the same detail when the operationId is replayed. */
	@PostMapping("/{orderId}/parts")
	ResponseEntity<RepairOrderDetail> consumePart(@AuthenticationPrincipal CurrentUser user, @PathVariable long orderId,
			@Valid @RequestBody ConsumePartRequest request) {
		return respond(partService.consume(user, orderId, request));
	}

	@PostMapping("/{orderId}/parts/{usageId}/returns")
	ResponseEntity<RepairOrderDetail> returnPart(@AuthenticationPrincipal CurrentUser user, @PathVariable long orderId,
			@PathVariable long usageId, @Valid @RequestBody ReturnPartRequest request) {
		return respond(partService.returnPart(user, orderId, usageId, request));
	}

	private static ResponseEntity<RepairOrderDetail> respond(OperationResult<RepairOrderDetail> result) {
		return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.body());
	}

}
