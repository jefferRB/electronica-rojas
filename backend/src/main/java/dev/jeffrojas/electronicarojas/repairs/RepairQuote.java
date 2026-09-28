package dev.jeffrojas.electronicarojas.repairs;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jeffrojas.electronicarojas.repairs.RepairEnums.DecisionMethod;
import dev.jeffrojas.electronicarojas.repairs.RepairEnums.QuoteDecision;
import dev.jeffrojas.electronicarojas.repairs.RepairEnums.QuoteStatus;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * Price the customer must authorize before the work continues. Amount in colones as BigDecimal /
 * NUMERIC(12,2), never float (DATA-006). No tax or invoicing logic (out of the MVP).
 * Once decided it is final: the entity refuses a second decision and a trigger (V6) rejects any
 * later UPDATE, so a rejected quote can never be treated as approved.
 */
@Entity
@Table(name = "repair_quotes")
public class RepairQuote {

	static final String CURRENCY = "CRC";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false, updatable = false)
	private RepairOrder order;

	@Column(nullable = false, precision = 12, scale = 2, updatable = false)
	private BigDecimal amount;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 3, updatable = false)
	private String currency;

	@Column(nullable = false, length = 1000, updatable = false)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private QuoteStatus status;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false, updatable = false)
	private AppUser createdBy;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decided_by")
	private AppUser decidedBy;

	private Instant decidedAt;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private DecisionMethod decisionMethod;

	@Column(length = 500)
	private String decisionNote;

	@Version
	private long version;

	protected RepairQuote() {
	}

	RepairQuote(RepairOrder order, BigDecimal amount, String description, AppUser createdBy, Instant now) {
		this.order = order;
		this.amount = amount;
		this.currency = CURRENCY;
		this.description = description.strip();
		this.status = QuoteStatus.PENDING;
		this.createdBy = createdBy;
		this.createdAt = now;
	}

	void decide(QuoteDecision decision, DecisionMethod method, String note, AppUser actor, Instant now) {
		if (status != QuoteStatus.PENDING) {
			throw new ConflictException("QUOTE_ALREADY_DECIDED", "The quote was already " + status + ".");
		}
		this.status = decision == QuoteDecision.APPROVED ? QuoteStatus.APPROVED : QuoteStatus.REJECTED;
		this.decisionMethod = method;
		this.decisionNote = note == null || note.isBlank() ? null : note.strip();
		this.decidedBy = actor;
		this.decidedAt = now;
	}

	public Long getId() {
		return id;
	}

	public RepairOrder getOrder() {
		return order;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public String getDescription() {
		return description;
	}

	public QuoteStatus getStatus() {
		return status;
	}

	public AppUser getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public AppUser getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public DecisionMethod getDecisionMethod() {
		return decisionMethod;
	}

	public String getDecisionNote() {
		return decisionNote;
	}

}
