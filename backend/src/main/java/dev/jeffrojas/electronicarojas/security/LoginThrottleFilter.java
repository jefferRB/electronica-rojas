package dev.jeffrojas.electronicarojas.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects login attempts while the account or client is blocked, BEFORE the password is checked,
 * so even the right password is refused during the block. Placed just before the form-login
 * filter; failures and successes are recorded by the login handlers in {@link SecurityConfig}.
 * <p>
 * Uses the TCP peer address only: X-Forwarded-For is not trusted until a reverse proxy is
 * configured explicitly (server.forward-headers-strategy).
 */
final class LoginThrottleFilter extends OncePerRequestFilter {

	private final LoginAttemptLimiter limiter;

	LoginThrottleFilter(LoginAttemptLimiter limiter) {
		this.limiter = limiter;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		// Path within the application: the servlet path alone is empty in some setups (e.g. MockMvc).
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return !("POST".equals(request.getMethod()) && SecurityConfig.LOGIN_URL.equals(path));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Optional<Duration> blocked = limiter.blockedFor(request.getParameter("email"), request.getRemoteAddr());
		if (blocked.isPresent()) {
			response.setHeader("Retry-After", String.valueOf(Math.max(1, blocked.get().toSeconds())));
			SecurityResponses.problem(response, HttpStatus.TOO_MANY_REQUESTS,
					"Too many failed login attempts. Try again later.");
			return;
		}
		chain.doFilter(request, response);
	}

}
