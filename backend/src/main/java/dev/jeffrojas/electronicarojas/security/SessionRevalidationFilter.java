package dev.jeffrojas.electronicarojas.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Revokes sessions whose account was deactivated, changed role or had its password reset since
 * login (BR-BRH-004). Comparable to ASP.NET Core Identity's security stamp validation, but
 * checked on every request instead of on an interval.
 * <p>
 * On revocation the security context is cleared and the session invalidated; the request then
 * continues as anonymous, so protected URLs answer 401 through the normal entry point.
 * <p>
 * Not a Spring bean on purpose: as a bean, Boot would also register it as a global servlet filter.
 */
final class SessionRevalidationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(SessionRevalidationFilter.class);

	private final UserDetailsService userDetailsService;

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	SessionRevalidationFilter(UserDetailsService userDetailsService) {
		this.userDetailsService = userDetailsService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = contextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentUser sessionUser
				&& !isStillValid(sessionUser)) {
			contextHolder.clearContext();
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
			log.info("Revoked session of user id {}: account disabled, role or credentials changed", sessionUser.id());
		}
		chain.doFilter(request, response);
	}

	private boolean isStillValid(CurrentUser sessionUser) {
		try {
			UserDetails fresh = userDetailsService.loadUserByUsername(sessionUser.getUsername());
			return fresh instanceof CurrentUser freshUser && sessionUser.isStillValidAgainst(freshUser);
		}
		catch (UsernameNotFoundException ex) {
			return false;
		}
	}

}
