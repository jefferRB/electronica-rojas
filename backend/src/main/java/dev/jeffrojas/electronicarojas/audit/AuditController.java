package dev.jeffrojas.electronicarojas.audit;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;

@RestController
class AuditController {

	private final AuditService auditService;

	AuditController(AuditService auditService) {
		this.auditService = auditService;
	}

	@GetMapping("/api/v1/audit-events")
	PageResponse<AuditEventResponse> list(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) Long branchId,
			@RequestParam(required = false) @Pattern(regexp = "[A-Z_]{1,40}") String entityType,
			@RequestParam(required = false) AuditAction action,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(AuditService.MAX_PAGE_SIZE) int size) {
		return auditService.list(user, new AuditService.AuditFilter(branchId, entityType, action, from, to), page,
				size);
	}

}
