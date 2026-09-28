package dev.jeffrojas.electronicarojas.shared.ping;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.jeffrojas.electronicarojas.security.LoginAttemptLimiter;
import dev.jeffrojas.electronicarojas.security.SecurityConfig;

/**
 * Web slice test: MVC + our SecurityFilterChain, without database or Docker.
 * PingService is replaced by a Mockito mock so only the HTTP contract is tested.
 */
@WebMvcTest(PingController.class)
@Import(SecurityConfig.class)
class PingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PingService pingService;

	/** Required by SecurityConfig; no user is ever loaded in these anonymous-only tests. */
	@MockitoBean
	private UserDetailsService userDetailsService;

	/** Required by SecurityConfig; the mock never blocks (Optional.empty()). */
	@MockitoBean
	private LoginAttemptLimiter loginAttemptLimiter;

	@Test
	void anonymousGetReturnsPingJson() throws Exception {
		given(pingService.ping()).willReturn(new PingResponse("ok", Instant.parse("2026-09-25T18:00:00Z")));

		mockMvc.perform(get("/api/v1/ping"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ok"))
			.andExpect(jsonPath("$.timestamp").value("2026-09-25T18:00:00Z"));
	}

	@Test
	void mutationWithoutCsrfTokenIsForbidden() throws Exception {
		mockMvc.perform(post("/api/v1/ping"))
			.andExpect(status().isForbidden());
	}

	@Test
	void anonymousMutationWithCsrfTokenStillRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/ping").with(csrf()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void anyOtherUrlRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/branches"))
			.andExpect(status().isUnauthorized());
	}

}
