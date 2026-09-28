package dev.jeffrojas.electronicarojas.notifications;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jeffrojas.electronicarojas.customers.ContactChannel;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.EventType;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SkipReason;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.Status;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SubjectType;

/**
 * One row of the transactional outbox: a notification to deliver after the business transaction
 * committed. JPA only inserts and reads it; status changes are guarded SQL updates in
 * {@link OutboxStore} (claim with SKIP LOCKED, fenced by the worker lease), so this read model is
 * {@code @Immutable}.
 */
@Entity
@Immutable
@Table(name = "notification_outbox")
public class OutboxMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String dedupeKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private EventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ContactChannel channel;

	@Column(nullable = false)
	private long customerId;

	@Column(nullable = false)
	private long branchId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SubjectType subjectType;

	@Column(nullable = false)
	private long subjectId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private Map<String, Object> payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private SkipReason skipReason;

	@Column(nullable = false)
	private int attempts;

	@Column(nullable = false)
	private int maxAttempts;

	@Column(nullable = false)
	private Instant nextAttemptAt;

	@Column(length = 300)
	private String lastError;

	@Column(length = 120)
	private String recipientHint;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	private Instant sentAt;

	protected OutboxMessage() {
	}

	OutboxMessage(String dedupeKey, NotificationRequest request, ContactChannel channel, Status status,
			SkipReason skipReason, int maxAttempts, Instant now) {
		this.dedupeKey = dedupeKey;
		this.eventType = request.eventType();
		this.channel = channel;
		this.customerId = request.customerId();
		this.branchId = request.branchId();
		this.subjectType = request.subjectType();
		this.subjectId = request.subjectId();
		this.payload = request.params();
		this.status = status;
		this.skipReason = skipReason;
		this.maxAttempts = maxAttempts;
		this.nextAttemptAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public EventType getEventType() {
		return eventType;
	}

	public ContactChannel getChannel() {
		return channel;
	}

	public long getCustomerId() {
		return customerId;
	}

	public long getBranchId() {
		return branchId;
	}

	public SubjectType getSubjectType() {
		return subjectType;
	}

	public long getSubjectId() {
		return subjectId;
	}

	public Map<String, Object> getPayload() {
		return payload;
	}

	public Status getStatus() {
		return status;
	}

	public SkipReason getSkipReason() {
		return skipReason;
	}

	public int getAttempts() {
		return attempts;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public String getLastError() {
		return lastError;
	}

	public String getRecipientHint() {
		return recipientHint;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	/** Order or request code of the subject, as recorded in the payload (for lists). */
	String reference() {
		Object code = payload.getOrDefault("orderCode", payload.get("requestCode"));
		return code == null ? null : code.toString();
	}

}
