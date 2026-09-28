package dev.jeffrojas.electronicarojas.notifications;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.AttemptOutcome;

/** Result of one delivery attempt (append-only): outcome and a short technical error, no content. */
@Entity
@Immutable
@Table(name = "notification_attempts")
public class NotificationAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private long outboxId;

	@Column(nullable = false)
	private int attemptNumber;

	@Column(nullable = false, length = 80)
	private String workerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AttemptOutcome outcome;

	@Column(length = 40)
	private String errorCode;

	@Column(length = 300)
	private String errorMessage;

	@Column(nullable = false)
	private Instant startedAt;

	@Column(nullable = false)
	private Instant finishedAt;

	protected NotificationAttempt() {
	}

	public int getAttemptNumber() {
		return attemptNumber;
	}

	public String getWorkerId() {
		return workerId;
	}

	public AttemptOutcome getOutcome() {
		return outcome;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

}
