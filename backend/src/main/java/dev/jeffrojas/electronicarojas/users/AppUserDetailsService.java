package dev.jeffrojas.electronicarojas.users;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.security.CurrentUser;

/**
 * Bridge between Spring Security and our accounts. Used by the login filter (through
 * DaoAuthenticationProvider, which also checks the password hash and {@code isEnabled}) and by
 * the session revalidation filter on every request.
 */
@Service
class AppUserDetailsService implements UserDetailsService {

	private final AppUserRepository users;

	AppUserDetailsService(AppUserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) {
		return users.findByEmail(AppUser.normalizeEmail(username))
			.map(AppUserDetailsService::toCurrentUser)
			.orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
	}

	private static CurrentUser toCurrentUser(AppUser user) {
		return new CurrentUser(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isActive(),
				user.getSecurityVersion(), user.getPasswordHash());
	}

}
