package dev.jeffrojas.electronicarojas.servicerequests;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PublicBranch;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PublicPortal;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PublicReceipt;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PublicRequestStatus;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PublicSubmission;

/**
 * The only anonymous business API (SecurityConfig permits /api/v1/public/**). CSRF still applies
 * to the POST: the SPA gets the token from /api/v1/auth/csrf like any other form.
 */
@RestController
@RequestMapping("/api/v1/public")
class PublicServiceRequestController {

	private final PublicServiceRequestService service;

	private final PortalSettingsService portal;

	PublicServiceRequestController(PublicServiceRequestService service, PortalSettingsService portal) {
		this.service = service;
		this.portal = portal;
	}

	/** The portal page by its shareable slug (current or previous); 404 for an unknown slug. */
	@GetMapping("/portal/{slug}")
	PublicPortal portal(@PathVariable String slug) {
		return portal.publicPortal(slug);
	}

	/** The current portal, for links made before slugs existed (/solicitar-servicio). */
	@GetMapping("/portal")
	PublicPortal currentPortal() {
		return portal.publicPortal(null);
	}

	@GetMapping("/branches")
	List<PublicBranch> branches() {
		return service.branches();
	}

	/** 202: the request was received, not scheduled (FR-SRV-002). */
	@PostMapping("/service-requests")
	@ResponseStatus(HttpStatus.ACCEPTED)
	PublicReceipt submit(@Valid @RequestBody PublicSubmission submission, HttpServletRequest http) {
		return service.submit(submission, http.getRemoteAddr());
	}

	@GetMapping("/service-requests/{publicRef}")
	PublicRequestStatus status(@PathVariable UUID publicRef) {
		return service.status(publicRef);
	}

}
