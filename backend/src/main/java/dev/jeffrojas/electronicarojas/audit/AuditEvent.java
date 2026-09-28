package dev.jeffrojas.electronicarojas.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only audit record. {@code @Immutable} stops Hibernate from ever issuing an UPDATE, and a
 * database trigger rejects UPDATE/DELETE from any other client (V4).
 * <p>
 * The actor is stored as id + name snapshot instead of a JPA relation to the users module, so the
 * audit module has no compile-time dependency on the modules it audits.
 */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(updatable = false)
	private Long actorId;

	@Column(length = 120, updatable = false)
	private String actorName;

	@Column(nullable = false, length = 60, updatable = false)
	private String action;

	@Column(nullable = false, length = 40, updatable = false)
	private String entityType;

	@Column(nullable = false, length = 40, updatable = false)
	private String entityId;

	@Column(updatable = false)
	private Long branchId;

	@Column(updatable = false)
	private UUID operationId;

	@Column(length = 64, updatable = false)
	private String correlationId;

	@Column(nullable = false, length = 300, updatable = false)
	private String summary;

	/** JSONB (V5). NULL for events written before structured details existed. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(updatable = false)
	private Map<String, Object> details;

	protected AuditEvent() {
	}

	AuditEvent(Instant occurredAt, Long actorId, String actorName, AuditEntry entry, String correlationId,
			String summary) {
		this.occurredAt = occurredAt;
		this.actorId = actorId;
		this.actorName = actorName;
		this.action = entry.action().name();
		this.entityType = entry.action().entityType();
		this.entityId = String.valueOf(entry.entityId());
		this.branchId = entry.branchId();
		this.operationId = entry.operationId();
		this.correlationId = correlationId;
		this.summary = summary;
		this.details = entry.details().isEmpty() ? null : entry.details();
	}

	public Long getId() {
		return id;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public Long getActorId() {
		return actorId;
	}

	public String getActorName() {
		return actorName;
	}

	public String getAction() {
		return action;
	}

	public String getEntityType() {
		return entityType;
	}

	public String getEntityId() {
		return entityId;
	}

	public Long getBranchId() {
		return branchId;
	}

	public UUID getOperationId() {
		return operationId;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public String getSummary() {
		return summary;
	}

	public Map<String, Object> getDetails() {
		return details;
	}

}
