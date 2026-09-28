package dev.jeffrojas.electronicarojas.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated principal stored in the HTTP session.
 * <p>
 * Holds identity and role only. Branch assignments are deliberately NOT cached here: they are
 * read from the database on every authorization decision so revoking one takes effect at once.
 */
public final class CurrentUser implements UserDetails, CredentialsContainer {

	/** Stored in the HTTP session, which the servlet container may serialize. */
	@Serial
	private static final long serialVersionUID = 1L;

	private final long id;
	private final String email;
	private final String fullName;
	private final Role role;
	private final boolean active;
	private final long securityVersion;
	private String passwordHash;

	public CurrentUser(long id, String email, String fullName, Role role, boolean active, long securityVersion,
			String passwordHash) {
		this.id = id;
		this.email = email;
		this.fullName = fullName;
		this.role = role;
		this.active = active;
		this.securityVersion = securityVersion;
		this.passwordHash = passwordHash;
	}

	/**
	 * The authenticated user of the current request, for use cases whose HTTP adapter does not
	 * pass the principal explicitly. Fails closed if there is none.
	 */
	public static CurrentUser fromSecurityContext() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
			return user;
		}
		throw new IllegalStateException("No authenticated application user in the security context");
	}

	public long id() {
		return id;
	}

	public String email() {
		return email;
	}

	public String fullName() {
		return fullName;
	}

	public Role role() {
		return role;
	}

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}

	/**
	 * True when {@code fresh} (just loaded from the database) still grants what this session was
	 * issued with. A deactivated account, a role change or a password reset revokes the session.
	 */
	public boolean isStillValidAgainst(CurrentUser fresh) {
		return fresh.id == id && fresh.active && fresh.role == role && fresh.securityVersion == securityVersion;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority(role.authority()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return active;
	}

	/** Called by Spring Security after authentication so the hash never lives in the session. */
	@Override
	public void eraseCredentials() {
		passwordHash = null;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof CurrentUser that && that.id == id;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "CurrentUser[id=" + id + ", role=" + role + "]";
	}

}
