package dev.jeffrojas.electronicarojas.servicerequests;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

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

import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ChangeBranchRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.DecisionRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.LinkCustomerRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ScheduleVisitRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ServiceRequestDetail;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ServiceRequestSummary;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.StaffRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.VisitView;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/service-requests")
class ServiceRequestController {

	private final ServiceRequestService requestService;

	private final VisitService visitService;

	ServiceRequestController(ServiceRequestService requestService, VisitService visitService) {
		this.requestService = requestService;
		this.visitService = visitService;
	}

	@GetMapping
	PageResponse<ServiceRequestSummary> list(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) Long branchId, @RequestParam(required = false) RequestStatus status,
			@RequestParam(required = false) Long customerId,
			@RequestParam(required = false) @Size(max = 100) String search,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(ServiceRequestService.MAX_PAGE_SIZE) int size) {
		return requestService.list(user, new ServiceRequestService.RequestFilter(branchId, status, customerId, search), page,
				size);
	}

	@PostMapping
	ResponseEntity<ServiceRequestDetail> create(@AuthenticationPrincipal CurrentUser user,
			@Valid @RequestBody StaffRequest request) {
		ServiceRequestDetail created = requestService.createByStaff(user, request);
		return ResponseEntity.created(URI.create("/api/v1/service-requests/" + created.id())).body(created);
	}

	@GetMapping("/{requestId}")
	ServiceRequestDetail get(@AuthenticationPrincipal CurrentUser user, @PathVariable long requestId) {
		return requestService.get(user, requestId);
	}

	@PostMapping("/{requestId}/review")
	ServiceRequestDetail review(@AuthenticationPrincipal CurrentUser user, @PathVariable long requestId) {
		return requestService.startReview(user, requestId);
	}

	@PutMapping("/{requestId}/customer")
	ServiceRequestDetail linkCustomer(@AuthenticationPrincipal CurrentUser user, @PathVariable long requestId,
			@Valid @RequestBody LinkCustomerRequest request) {
		return requestService.linkCustomer(user, requestId, request);
	}

	/** 204 when the request moved out of the caller's own scope. */
	@PutMapping("/{requestId}/branch")
	ResponseEntity<ServiceRequestDetail> changeBranch(@AuthenticationPrincipal CurrentUser user,
			@PathVariable long requestId, @Valid @RequestBody ChangeBranchRequest request) {
		ServiceRequestDetail detail = requestService.changeBranch(user, requestId, request);
		return detail == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(detail);
	}

	@PostMapping("/{requestId}/reject")
	ServiceRequestDetail reject(@AuthenticationPrincipal CurrentUser user, @PathVariable long requestId,
			@Valid @RequestBody DecisionRequest request) {
		return requestService.reject(user, requestId, request);
	}

	@PostMapping("/{requestId}/cancel")
	ServiceRequestDetail cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable long requestId,
			@Valid @RequestBody DecisionRequest request) {
		return requestService.cancel(user, requestId, request);
	}

	@PostMapping("/{requestId}/visits")
	ResponseEntity<VisitView> schedule(@AuthenticationPrincipal CurrentUser user, @PathVariable long requestId,
			@Valid @RequestBody ScheduleVisitRequest request) {
		VisitView visit = visitService.schedule(user, requestId, request);
		return ResponseEntity.created(URI.create("/api/v1/service-visits/" + visit.id())).body(visit);
	}

}
