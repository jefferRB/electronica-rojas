package dev.jeffrojas.electronicarojas.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;

/** SEC-006 through the real filter chain. Recovery over time is covered by LoginAttemptLimiterTest. */
class LoginThrottlingIntegrationTests extends IntegrationTestSupport {

	private ResultActions attempt(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").with(xsrf()).param("email", email).param("password", password));
	}

	@Test
	void fiveFailuresBlockTheAccountEvenForTheRightPassword() throws Exception {
		for (int i = 0; i < 5; i++) {
			attempt(ADMIN_EMAIL, "wrong-password-" + i).andExpect(status().isUnauthorized());
		}

		attempt(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.detail").value("Too many failed login attempts. Try again later."));
	}

	@Test
	void unknownEmailsAreThrottledIdenticallySoNothingIsRevealed() throws Exception {
		for (int i = 0; i < 5; i++) {
			attempt("nobody@electronica-rojas.test", "whatever-password").andExpect(status().isUnauthorized());
		}

		attempt("nobody@electronica-rojas.test", "whatever-password").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.detail").value("Too many failed login attempts. Try again later."));
	}

	@Test
	void anotherAccountIsNotAffectedAndSuccessResetsTheCounter() throws Exception {
		createUser("tech@electronica-rojas.test", Role.TECHNICIAN, createBranch("SJ-01"));
		for (int i = 0; i < 5; i++) {
			attempt("tech@electronica-rojas.test", "wrong-password").andExpect(status().isUnauthorized());
		}

		attempt(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isNoContent());

		for (int i = 0; i < 4; i++) {
			attempt(ADMIN_EMAIL, "wrong-password").andExpect(status().isUnauthorized());
		}
		attempt(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isNoContent());
		for (int i = 0; i < 4; i++) {
			attempt(ADMIN_EMAIL, "wrong-password").andExpect(status().isUnauthorized());
		}
		// The counter was reset by the previous success, so 4 + 4 failures never reach the limit.
		attempt(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isNoContent());
	}

}
