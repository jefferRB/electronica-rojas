package dev.jeffrojas.electronicarojas.notifications;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.notifications.NotificationDtos.AttemptView;
import dev.jeffrojas.electronicarojas.notifications.NotificationDtos.InboxMessage;
import dev.jeffrojas.electronicarojas.notifications.NotificationDtos.NotificationDetail;
import dev.jeffrojas.electronicarojas.notifications.NotificationDtos.NotificationRow;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.Status;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;
import dev.jeffrojas.electronicarojas.shared.web.PageResponse;

/**
 * Operational view of notifications (C.4): ADMIN sees all, BRANCH_MANAGER the notifications of
 * their branches (same scope as the audit trail). Shows states, attempts and errors, never message
 * bodies or full addresses; a failed message can be retried by hand.
 */
@Service
@Transactional(readOnly = true)
public class NotificationAdminService {

	static final int MAX_PAGE_SIZE = 100;

	private static final List<Long> NO_BRANCHES = List.of(-1L);

	private static final String NOT_FOUND = "Notification not found.";

	private final OutboxMessageRepository messages;

	private final NotificationAttemptRepository attempts;

	private final OutboxStore store;

	private final BranchService branchService;

	private final MailTransport transport;

	private final AuditService audit;

	private final EntityManager entityManager;

	private final Clock clock;

	NotificationAdminService(OutboxMessageRepository messages, NotificationAttemptRepository attempts, OutboxStore store,
			BranchService branchService, MailTransport transport, AuditService audit, EntityManager entityManager, Clock clock) {
		this.messages = messages;
		this.attempts = attempts;
		this.store = store;
		this.branchService = branchService;
		this.transport = transport;
		this.audit = audit;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
	public PageResponse<NotificationRow> list(CurrentUser user, Long branchId, Status status, Instant from, int page,
			int size) {
		boolean restrict = !user.isAdmin();
		List<Long> branchIds = restrict ? branchService.listSelectable(user).stream().map(BranchSummary::id).toList()
				: NO_BRANCHES;
		return PageResponse.of(messages.search(restrict, branchIds.isEmpty() ? NO_BRANCHES : branchIds, branchId, status,
				from, PageRequest.of(page, size)), NotificationRow::from);
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
	public NotificationDetail get(CurrentUser user, long id) {
		OutboxMessage message = requireVisible(user, id);
		return new NotificationDetail(NotificationRow.from(message),
				attempts.findByOutboxIdOrderByIdAsc(id).stream().map(AttemptView::from).toList());
	}

	/** FAILED -> PENDING with one more attempt; anything else is a 409 (never re-send a SENT one). */
	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
	@Transactional
	public NotificationDetail retry(CurrentUser user, long id) {
		OutboxMessage message = requireVisible(user, id);
		if (!store.retry(id, clock.instant())) {
			throw new ConflictException("NOTIFICATION_NOT_RETRYABLE", "Only failed notifications can be retried.",
					Map.of("status", message.getStatus().name()));
		}
		audit.record(user, AuditEntry.of(AuditAction.NOTIFICATION_RETRY_REQUESTED, id)
			.branch(message.getBranchId())
			.detail("eventType", message.getEventType())
			.detail("reference", message.reference())
			.detail("attempts", message.getAttempts())
			.summary("Retry of notification " + id + " requested"));
		// The row changed through SQL: drop the cached copy of the read model and read it again.
		entityManager.detach(message);
		return get(user, id);
	}

	/** Development inbox: what customers would have received. 404 when a real transport is used. */
	@PreAuthorize("hasRole('ADMIN')")
	public List<InboxMessage> devInbox() {
		if (!(transport instanceof InboxMailTransport inbox)) {
			throw new NotFoundException("The development inbox is only available in inbox mode.");
		}
		return inbox.messages();
	}

	private OutboxMessage requireVisible(CurrentUser user, long id) {
		OutboxMessage message = messages.findById(id).orElseThrow(() -> new NotFoundException(NOT_FOUND));
		if (!branchService.isReadable(user, message.getBranchId())) {
			throw new NotFoundException(NOT_FOUND);
		}
		return message;
	}

}
