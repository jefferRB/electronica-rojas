package dev.jeffrojas.electronicarojas.servicerequests;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PortalSettingsRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PortalSettingsView;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PortalSlugRequest;

/** Administration of the public home-service portal (ADMIN only, enforced in the service). */
@RestController
@RequestMapping("/api/v1/portal-settings")
class PortalSettingsController {

	private final PortalSettingsService service;

	PortalSettingsController(PortalSettingsService service) {
		this.service = service;
	}

	@GetMapping
	PortalSettingsView get() {
		return service.settings();
	}

	@PutMapping
	PortalSettingsView update(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody PortalSettingsRequest request) {
		return service.update(user, request);
	}

	/** Its own request: changing the address is a deliberate, confirmed act (BR-SRV-009). */
	@PutMapping("/slug")
	PortalSettingsView changeSlug(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody PortalSlugRequest request) {
		return service.changeSlug(user, request);
	}

}
