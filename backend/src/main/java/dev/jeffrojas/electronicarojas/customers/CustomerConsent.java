package dev.jeffrojas.electronicarojas.customers;

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

import org.hibernate.annotations.Immutable;

import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * One consent statement of a customer for one channel (granted or withdrawn), append-only. The
 * current consent is the latest statement; the history is never rewritten (BR-CUS-006).
 */
@Entity
@Immutable
@Table(name = "customer_consents")
public class CustomerConsent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false, updatable = false)
	private Customer customer;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private ContactChannel channel;

	@Column(nullable = false, updatable = false)
	private boolean granted;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private ConsentSource source;

	@Column(length = 20, updatable = false)
	private String textVersion;

	@Column(length = 40, updatable = false)
	private String reference;

	@Column(nullable = false, updatable = false)
	private Instant statedAt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recorded_by", nullable = false, updatable = false)
	private AppUser recordedBy;

	@Column(nullable = false, updatable = false)
	private Instant recordedAt;

	protected CustomerConsent() {
	}

	CustomerConsent(Customer customer, ContactChannel channel, boolean granted, ConsentSource source, String textVersion,
			String reference, Instant statedAt, AppUser recordedBy, Instant now) {
		this.customer = customer;
		this.channel = channel;
		this.granted = granted;
		this.source = source;
		this.textVersion = textVersion;
		this.reference = reference;
		this.statedAt = statedAt;
		this.recordedBy = recordedBy;
		this.recordedAt = now;
	}

	public Long getId() {
		return id;
	}

	public ContactChannel getChannel() {
		return channel;
	}

	public boolean isGranted() {
		return granted;
	}

	public ConsentSource getSource() {
		return source;
	}

	public String getTextVersion() {
		return textVersion;
	}

	public String getReference() {
		return reference;
	}

	public Instant getStatedAt() {
		return statedAt;
	}

	public AppUser getRecordedBy() {
		return recordedBy;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

}
