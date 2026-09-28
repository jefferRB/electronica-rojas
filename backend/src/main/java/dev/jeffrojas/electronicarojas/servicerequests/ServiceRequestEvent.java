package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Instant;
import java.util.Map;

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

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.EventType;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * One entry of a request's timeline (decisions, visit changes). Append-only in JPA and in the
 * database. {@code details} holds codes and instants (e.g. the previous and new time of a
 * reschedule), never free text with personal data.
 */
@Entity
@Immutable
@Table(name = "service_request_events")
public class ServiceRequestEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false, updatable = false)
	private ServiceRequest request;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "visit_id", updatable = false)
	private ServiceVisit visit;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40, updatable = false)
	private EventType eventType;

	@Column(length = 20, updatable = false)
	private String fromStatus;

	@Column(length = 20, updatable = false)
	private String toStatus;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(updatable = false)
	private Map<String, Object> details;

	@Column(length = 500, updatable = false)
	private String reason;

	/** Null for the public form. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_id", updatable = false)
	private AppUser actor;

	@Column(nullable = false, updatable = false)
	private Instant occurredAt;

	protected ServiceRequestEvent() {
	}

	ServiceRequestEvent(ServiceRequest request, ServiceVisit visit, EventType eventType, Enum<?> fromStatus,
			Enum<?> toStatus, Map<String, Object> details, String reason, AppUser actor, Instant occurredAt) {
		this.request = request;
		this.visit = visit;
		this.eventType = eventType;
		this.fromStatus = fromStatus == null ? null : fromStatus.name();
		this.toStatus = toStatus == null ? null : toStatus.name();
		this.details = details == null || details.isEmpty() ? null : details;
		this.reason = reason;
		this.actor = actor;
		this.occurredAt = occurredAt;
	}

	public Long getId() {
		return id;
	}

	public ServiceVisit getVisit() {
		return visit;
	}

	public EventType getEventType() {
		return eventType;
	}

	public String getFromStatus() {
		return fromStatus;
	}

	public String getToStatus() {
		return toStatus;
	}

	public Map<String, Object> getDetails() {
		return details;
	}

	public String getReason() {
		return reason;
	}

	public AppUser getActor() {
		return actor;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

}
