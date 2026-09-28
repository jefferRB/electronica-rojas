package dev.jeffrojas.electronicarojas.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.ClockConfig;
import dev.jeffrojas.electronicarojas.shared.web.CorrelationIdFilter;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;

/**
 * Functional audit trail (OBS-002). Writing requires an existing transaction
 * ({@code MANDATORY}): the event is committed or rolled back together with the business change
 * it describes, so the trail can never claim something that did not happen.
 */
@Service
public class AuditService {

	static final int MAX_PAGE_SIZE = 100;

	private static final int MAX_SUMMARY = 300;

	/** Business dates are Costa Rica calendar days (BR-DAT-002). */
	static final ZoneId BUSINESS_ZONE = ClockConfig.BUSINESS_ZONE;

	private final AuditEventRepository events;

	private final Clock clock;

	AuditService(AuditEventRepository events, Clock clock) {
		this.events = events;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(CurrentUser actor, AuditEntry entry) {
		save(actor.id(), actor.fullName(), entry);
	}

	/** Actions without a human actor, e.g. the configured admin bootstrap. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordSystem(AuditEntry entry) {
		save(null, null, entry);
	}

	/**
	 * FR-AUD-001: ADMIN reads everything; BRANCH_MANAGER only events of its active branches (the
	 * scope is part of the SQL, so a foreign {@code branchId} simply yields no rows). Dates are
	 * calendar days in America/Costa_Rica, both inclusive; storage stays UTC.
	 */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
	@Transactional(readOnly = true)
	public PageResponse<AuditEventResponse> list(CurrentUser user, AuditFilter filter, int page, int size) {
		if (filter.from() != null && filter.to() != null && filter.to().isBefore(filter.from())) {
			throw new InvalidRequestException("to", "DATE_RANGE_INVALID", "must not be before from");
		}
		Instant from = filter.from() == null ? null : filter.from().atStartOfDay(BUSINESS_ZONE).toInstant();
		Instant to = filter.to() == null ? null : filter.to().plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
		String action = filter.action() == null ? null : filter.action().name();
		PageRequest request = PageRequest.of(page, size);
		var result = user.isAdmin()
				? events.search(filter.branchId(), filter.entityType(), action, from, to, request)
				: events.searchForAssignedBranches(user.id(), filter.branchId(), filter.entityType(), action, from, to,
						request);
		return PageResponse.of(result, AuditEventResponse::from);
	}

	/** Filters of the audit list; every field is optional. */
	public record AuditFilter(Long branchId, String entityType, AuditAction action, LocalDate from, LocalDate to) {
	}

	private void save(Long actorId, String actorName, AuditEntry entry) {
		events.save(new AuditEvent(clock.instant(), actorId, actorName, entry, MDC.get(CorrelationIdFilter.MDC_KEY),
				sanitize(entry.summary())));
	}

	/** One line, bounded length: summaries may include user-typed names or reasons. */
	static String sanitize(String summary) {
		String singleLine = summary.replaceAll("[\\p{Cntrl}]+", " ").strip();
		return singleLine.length() <= MAX_SUMMARY ? singleLine : singleLine.substring(0, MAX_SUMMARY - 1) + "…";
	}

}
