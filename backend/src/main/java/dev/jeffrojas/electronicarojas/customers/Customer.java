package dev.jeffrojas.electronicarojas.customers;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * A person who brings appliances or requests visits (not a collaborator account). One record per
 * person across all branches, so their repair history stays together (FR-CUS-001).
 */
@Entity
@Table(name = "customers")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 160)
	private String fullName;

	/** {@link SearchText#normalize} of the name: what incremental search compares against. */
	@Column(nullable = false, length = 160)
	private String searchName;

	/** E.164, normalized by {@link PhoneNumbers}. */
	@Column(nullable = false, length = 16)
	private String phone;

	@Column(length = 254)
	private String email;

	@Column(length = 300)
	private String address;

	/** Internal, never shown to the customer and never written to logs. */
	@Column(length = 1000)
	private String internalNotes;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "registered_branch_id", nullable = false, updatable = false)
	private Branch registeredBranch;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false, updatable = false)
	private AppUser createdBy;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected Customer() {
	}

	Customer(CustomerData data, Branch registeredBranch, AppUser createdBy, Instant now) {
		this.registeredBranch = registeredBranch;
		this.createdBy = createdBy;
		this.createdAt = now;
		apply(data, now);
	}

	void update(CustomerData data, Instant now) {
		apply(data, now);
	}

	private void apply(CustomerData data, Instant now) {
		this.fullName = data.fullName().strip().replaceAll("\\s+", " ");
		this.searchName = SearchText.normalize(fullName);
		this.phone = data.phone();
		this.email = blankToNull(data.email()) == null ? null : data.email().strip().toLowerCase(Locale.ROOT);
		this.address = blankToNull(data.address());
		this.internalNotes = blankToNull(data.internalNotes());
		this.updatedAt = now;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	/** Validated, already normalized input for create/update ({@code phone} in E.164). */
	record CustomerData(String fullName, String phone, String email, String address, String internalNotes) {
	}

	public Long getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public String getSearchName() {
		return searchName;
	}

	public String getPhone() {
		return phone;
	}

	public String getEmail() {
		return email;
	}

	public String getAddress() {
		return address;
	}

	public String getInternalNotes() {
		return internalNotes;
	}

	public Branch getRegisteredBranch() {
		return registeredBranch;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getVersion() {
		return version;
	}

}
