package dev.jeffrojas.electronicarojas.users;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.security.Role;

/**
 * Internal collaborator account (not a customer). There is no self-registration: only an
 * administrator (or the controlled bootstrap) creates accounts (BR-BRH-003, SEC-004).
 */
@Entity
@Table(name = "app_users")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 254, updatable = false)
	private String email;

	@Column(nullable = false, length = 120)
	private String fullName;

	@Column(nullable = false, length = 255)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Role role;

	@Column(nullable = false)
	private boolean active;

	@Column(nullable = false)
	private long securityVersion;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	/**
	 * UserBranch assignments. Owning side of the relation: changes to this set are written to
	 * user_branches. LAZY so loading a user for login does not load its branches.
	 */
	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "user_branches",
			joinColumns = @JoinColumn(name = "user_id"),
			inverseJoinColumns = @JoinColumn(name = "branch_id"))
	private Set<Branch> branches = new HashSet<>();

	protected AppUser() {
	}

	AppUser(String email, String fullName, String passwordHash, Role role, Instant now) {
		this.email = normalizeEmail(email);
		this.fullName = fullName.strip();
		this.passwordHash = passwordHash;
		this.role = role;
		this.active = true;
		this.createdAt = now;
		this.updatedAt = now;
	}

	static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	void rename(String fullName, Instant now) {
		this.fullName = fullName.strip();
		this.updatedAt = now;
	}

	/** Role or status changes revoke open sessions through {@code securityVersion}. */
	void changeAccess(Role newRole, boolean newActive, Instant now) {
		if (newRole != role || newActive != active) {
			this.role = newRole;
			this.active = newActive;
			this.securityVersion++;
			this.updatedAt = now;
		}
	}

	void changePasswordHash(String newPasswordHash, Instant now) {
		this.passwordHash = newPasswordHash;
		this.securityVersion++;
		this.updatedAt = now;
	}

	/** Administrators access every branch, so no assignments are stored for them. */
	void assignBranches(Collection<Branch> newBranches, Instant now) {
		branches.clear();
		if (role != Role.ADMIN) {
			branches.addAll(newBranches);
		}
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getFullName() {
		return fullName;
	}

	String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public boolean isActive() {
		return active;
	}

	long getSecurityVersion() {
		return securityVersion;
	}

	public long getVersion() {
		return version;
	}

	public Set<Branch> getBranches() {
		return Set.copyOf(branches);
	}

}
