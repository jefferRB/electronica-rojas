package dev.jeffrojas.electronicarojas.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * JSON responses produced by the security filter chain (before any controller runs), shaped
 * as RFC 9457 ProblemDetail like the rest of the API (ARCH-API-002). Messages are fixed text:
 * no user input, no exception details.
 */
final class SecurityResponses {

	private SecurityResponses() {
	}

	/** Anonymous caller on a protected URL. */
	static void unauthorized(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
			throws IOException {
		problem(response, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	/** Authenticated without permission, or a mutation without a valid CSRF token. */
	static void forbidden(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
			throws IOException {
		problem(response, HttpStatus.FORBIDDEN, "Access denied.");
	}

	/**
	 * Same body for unknown email, wrong password and disabled account, so the response does not
	 * reveal whether an account exists (FR-AUTH-001).
	 */
	static void loginFailed(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
			throws IOException {
		problem(response, HttpStatus.UNAUTHORIZED, "Invalid email or password.");
	}

	/** The SPA then calls GET /api/v1/auth/me to load the session details. */
	static void loginSucceeded(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		response.setStatus(HttpStatus.NO_CONTENT.value());
	}

	static void problem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter()
			.write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase() + "\",\"status\":" + status.value()
					+ ",\"detail\":\"" + detail + "\"}");
	}

}
