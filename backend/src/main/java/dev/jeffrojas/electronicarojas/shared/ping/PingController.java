package dev.jeffrojas.electronicarojas.shared.ping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Temporary demo endpoint that shows the Controller -> Service flow (ARCH-API-003).
 * Infrastructure health lives in Actuator at {@code /actuator/health}.
 */
@RestController
@RequestMapping("/api/v1/ping")
class PingController {

	private final PingService pingService;

	PingController(PingService pingService) {
		this.pingService = pingService;
	}

	@GetMapping
	PingResponse ping() {
		return pingService.ping();
	}

}
