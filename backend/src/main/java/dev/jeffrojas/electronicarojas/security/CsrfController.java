package dev.jeffrojas.electronicarojas.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issues the {@code XSRF-TOKEN} cookie. Spring Security loads CSRF tokens lazily, so the SPA
 * calls this before its first mutation and again after login/logout, which rotate the token.
 */
@RestController
class CsrfController {

	static final String CSRF_URL = "/api/v1/auth/csrf";

	@GetMapping(CSRF_URL)
	ResponseEntity<Void> csrf(CsrfToken token) {
		token.getToken(); // forces the deferred token to be generated and written as a cookie
		return ResponseEntity.noContent().build();
	}

}
