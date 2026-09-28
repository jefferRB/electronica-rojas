package dev.jeffrojas.electronicarojas.notifications;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jeffrojas.electronicarojas.notifications.NotificationDtos.InboxMessage;
import dev.jeffrojas.electronicarojas.notifications.NotificationDtos.NotificationDetail;
import dev.jeffrojas.electronicarojas.notifications.NotificationDtos.NotificationRow;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.Status;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.ClockConfig;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

	private final NotificationAdminService service;

	NotificationController(NotificationAdminService service) {
		this.service = service;
	}

	/** {@code from}: calendar day in Costa Rica (inclusive). */
	@GetMapping
	PageResponse<NotificationRow> list(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) Long branchId, @RequestParam(required = false) Status status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(NotificationAdminService.MAX_PAGE_SIZE) int size) {
		return service.list(user, branchId, status,
				from == null ? null : from.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant(), page, size);
	}

	/** Declared before /{id}: the development inbox (ADMIN, inbox mode only). */
	@GetMapping("/dev-inbox")
	List<InboxMessage> devInbox() {
		return service.devInbox();
	}

	@GetMapping("/{id}")
	NotificationDetail get(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
		return service.get(user, id);
	}

	@PostMapping("/{id}/retry")
	NotificationDetail retry(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
		return service.retry(user, id);
	}

}
