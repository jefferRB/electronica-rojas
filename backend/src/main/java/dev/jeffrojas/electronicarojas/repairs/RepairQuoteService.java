package dev.jeffrojas.electronicarojas.repairs;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.CreateQuoteRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.QuoteDecisionRequest;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.RepairOrderDetail;
import dev.jeffrojas.electronicarojas.repairs.RepairEnums.QuoteDecision;
import dev.jeffrojas.electronicarojas.repairs.RepairEnums.QuoteStatus;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * Quotes (FR-REP-004, BR-REP-004): creating one puts the order in AWAITING_APPROVAL; recording the
 * customer's decision moves it to APPROVED or CANCELLED. Quote, status, history and audit change
 * in one transaction under the order's row lock. No invoicing and no stock consumption here.
 */
@Service
@Transactional
public class RepairQuoteService {

	/** Reason written to the order timeline when the customer rejects the quote. */
	static final String REJECTION_REASON = "Cotización rechazada por el cliente";

	private final RepairQuoteRepository quotes;

	private final RepairOrderAccess access;

	private final RepairWorkflow workflow;

	private final RepairDetailAssembler assembler;

	private final AuditService audit;

	private final EntityManager entityManager;

	private final Clock clock;

	RepairQuoteService(RepairQuoteRepository quotes, RepairOrderAccess access, RepairWorkflow workflow,
			RepairDetailAssembler assembler, AuditService audit, EntityManager entityManager, Clock clock) {
		this.quotes = quotes;
		this.access = access;
		this.workflow = workflow;
		this.assembler = assembler;
		this.audit = audit;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'TECHNICIAN')")
	public RepairOrderDetail create(CurrentUser user, long orderId, CreateQuoteRequest request) {
		RepairOrder order = access.requireVisibleForUpdate(user, orderId);
		if (quotes.existsByOrderIdAndStatus(order.getId(), QuoteStatus.PENDING)) {
			throw new ConflictException("QUOTE_PENDING_EXISTS", "The order already has a quote awaiting a decision.");
		}
		if (!RepairPolicy.mayCreateQuote(user, order)) {
			throw new ConflictException("QUOTE_NOT_ALLOWED", "A quote cannot be issued in status " + order.getStatus() + ".",
					Map.of("status", order.getStatus().name()));
		}
		Instant now = clock.instant();
		RepairQuote quote = quotes.saveAndFlush(new RepairQuote(order, request.amount(), request.description(),
				entityManager.getReference(AppUser.class, user.id()), now));
		workflow.change(user, order, RepairStatus.AWAITING_APPROVAL, null, true);
		audit.record(user, AuditEntry.of(AuditAction.REPAIR_QUOTE_CREATED, quote.getId())
			.branch(order.getBranch().getId())
			.detail("orderCode", order.getOrderCode())
			.detail("amount", quote.getAmount().toPlainString())
			.detail("currency", quote.getCurrency())
			.summary("Quote " + quote.getId() + " for order " + order.getOrderCode()));
		return assembler.detail(user, order);
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST')")
	public RepairOrderDetail decide(CurrentUser user, long orderId, long quoteId, QuoteDecisionRequest request) {
		RepairOrder order = access.requireVisibleForUpdate(user, orderId);
		RepairQuote quote = quotes.findByIdAndOrderId(quoteId, order.getId())
			.orElseThrow(() -> new NotFoundException("Quote not found."));
		// Entity check first: a decided quote (approved or rejected) can never be decided again.
		quote.decide(request.decision(), request.method(), request.note(),
				entityManager.getReference(AppUser.class, user.id()), clock.instant());
		quotes.saveAndFlush(quote);
		if (request.decision() == QuoteDecision.APPROVED) {
			workflow.change(user, order, RepairStatus.APPROVED, null, true);
		}
		else {
			workflow.change(user, order, RepairStatus.CANCELLED, REJECTION_REASON, true);
		}
		audit.record(user, AuditEntry.of(AuditAction.REPAIR_QUOTE_DECIDED, quote.getId())
			.branch(order.getBranch().getId())
			.detail("orderCode", order.getOrderCode())
			.detail("decision", request.decision())
			.detail("method", request.method())
			.detail("amount", quote.getAmount().toPlainString())
			.detail("currency", quote.getCurrency())
			.summary("Quote " + quote.getId() + " " + request.decision() + " for order " + order.getOrderCode()));
		return assembler.detail(user, order);
	}

}
