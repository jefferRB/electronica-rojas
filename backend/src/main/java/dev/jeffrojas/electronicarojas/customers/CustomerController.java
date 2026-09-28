package dev.jeffrojas.electronicarojas.customers;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CreateCustomerRequest;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CustomerConsents;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CustomerResponse;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.RecordConsentRequest;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CustomerLookupResponse;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.CustomerMatch;
import dev.jeffrojas.electronicarojas.customers.CustomerDtos.UpdateCustomerRequest;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/customers")
class CustomerController {

	private final CustomerService customerService;

	private final CustomerConsentService consentService;

	CustomerController(CustomerService customerService, CustomerConsentService consentService) {
		this.customerService = customerService;
		this.consentService = consentService;
	}

	/** Search by name, email or phone digits, within the caller's customer scope. */
	@GetMapping
	PageResponse<CustomerResponse> search(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) @Size(max = 100) String search,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(CustomerService.MAX_PAGE_SIZE) int size) {
		return customerService.search(user, search, page, size);
	}

	@GetMapping("/phone-matches")
	List<CustomerMatch> phoneMatches(@AuthenticationPrincipal CurrentUser user,
			@RequestParam @NotBlank @Size(max = 30) String phone) {
		return customerService.phoneMatches(user, phone);
	}

	/** Incremental search for the customer picker: name words and/or phone fragment. */
	@GetMapping("/lookup")
	CustomerLookupResponse lookup(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) @Size(max = 160) String name,
			@RequestParam(required = false) @Size(max = 30) String phone,
			@RequestParam(defaultValue = "8") @Min(1) @Max(CustomerService.MAX_LOOKUP_SIZE) int size) {
		return customerService.lookup(user, name, phone, size);
	}

	@GetMapping("/{customerId}")
	CustomerResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable long customerId) {
		return customerService.get(user, customerId);
	}

	@PostMapping
	ResponseEntity<CustomerResponse> create(@AuthenticationPrincipal CurrentUser user,
			@Valid @RequestBody CreateCustomerRequest request) {
		CustomerResponse created = customerService.create(user, request);
		return ResponseEntity.created(URI.create("/api/v1/customers/" + created.id())).body(created);
	}

	@PutMapping("/{customerId}")
	CustomerResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable long customerId,
			@Valid @RequestBody UpdateCustomerRequest request) {
		return customerService.update(user, customerId, request);
	}

	/** Current notification consent per channel and the full history of statements. */
	@GetMapping("/{customerId}/consents")
	CustomerConsents consents(@AuthenticationPrincipal CurrentUser user, @PathVariable long customerId) {
		return consentService.consents(user, customerId);
	}

	@PostMapping("/{customerId}/consents")
	CustomerConsents recordConsent(@AuthenticationPrincipal CurrentUser user, @PathVariable long customerId,
			@Valid @RequestBody RecordConsentRequest request) {
		return consentService.record(user, customerId, request);
	}

}
