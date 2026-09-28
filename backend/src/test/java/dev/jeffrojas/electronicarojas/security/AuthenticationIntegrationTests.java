package dev.jeffrojas.electronicarojas.security;

import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;

/** FR-AUTH-001..003, SEC-001/002, BR-BRH-004 against the real filter chain and PostgreSQL. */
class AuthenticationIntegrationTests extends IntegrationTestSupport {

	@LocalServerPort
	private int port;

	@Test
	void loginCreatesSessionAndMeReturnsProfileWithScopedBranches() throws Exception {
		long own = createBranch("SJ-01");
		createBranch("AL-01");
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, own);

		MockHttpSession session = login("manager@electronica-rojas.test");

		String body = mockMvc.perform(get("/api/v1/auth/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.email").value("manager@electronica-rojas.test"))
			.andExpect(jsonPath("$.user.role").value("BRANCH_MANAGER"))
			.andExpect(jsonPath("$.branches.length()").value(1))
			.andExpect(jsonPath("$.branches[0].code").value("SJ-01"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContainIgnoringCase("password").doesNotContain("{bcrypt}");
	}

	@Test
	void emailIsMatchedCaseInsensitively() throws Exception {
		createUser("mixed@electronica-rojas.test", Role.RECEPTIONIST, createBranch("SJ-01"));

		login("  Mixed@Electronica-Rojas.TEST ");
	}

	@Test
	void loginRotatesTheSessionIdToPreventFixation() throws Exception {
		createUser("tech@electronica-rojas.test", Role.TECHNICIAN, createBranch("SJ-01"));
		MockHttpSession preLoginSession = new MockHttpSession();
		String idBeforeLogin = preLoginSession.getId();

		mockMvc
			.perform(post("/api/v1/auth/login").session(preLoginSession)
				.with(xsrf())
				.param("email", "tech@electronica-rojas.test")
				.param("password", USER_PASSWORD))
			.andExpect(status().isNoContent());

		assertThat(preLoginSession.getId()).isNotEqualTo(idBeforeLogin);
	}

	@Test
	void failedLoginsAreIndistinguishable() throws Exception {
		createUser("disabled@electronica-rojas.test", Role.RECEPTIONIST, createBranch("SJ-01"));
		jdbc.update("UPDATE app_users SET active = FALSE WHERE email = 'disabled@electronica-rojas.test'");

		String wrongPassword = failedLogin(ADMIN_EMAIL, "not-the-password-123");
		String unknownEmail = failedLogin("nobody@electronica-rojas.test", "whatever-password-1");
		String disabledAccount = failedLogin("disabled@electronica-rojas.test", USER_PASSWORD);

		assertThat(wrongPassword).contains("Invalid email or password.");
		assertThat(unknownEmail).isEqualTo(wrongPassword);
		assertThat(disabledAccount).isEqualTo(wrongPassword);
	}

	@Test
	void loginWithoutCsrfTokenIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").param("email", ADMIN_EMAIL).param("password", ADMIN_PASSWORD))
			.andExpect(status().isForbidden());
	}

	@Test
	void anonymousCallerGets401ProblemDetail() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void logoutRequiresPostWithCsrfAndInvalidatesSession() throws Exception {
		MockHttpSession session = loginAsAdmin();

		mockMvc.perform(post("/api/v1/auth/logout").session(session)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/auth/logout").session(session)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/auth/logout").session(session).with(xsrf())).andExpect(status().isNoContent());

		assertThat(session.isInvalid()).isTrue();
		mockMvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void deactivatingAnAccountRevokesItsOpenSession() throws Exception {
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, createBranch("SJ-01"));
		MockHttpSession session = login("manager@electronica-rojas.test");

		jdbc.update("UPDATE app_users SET active = FALSE WHERE email = 'manager@electronica-rojas.test'");

		mockMvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void changingTheRoleRevokesTheOpenSession() throws Exception {
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, createBranch("SJ-01"));
		MockHttpSession session = login("manager@electronica-rojas.test");

		jdbc.update("UPDATE app_users SET role = 'ADMIN' WHERE email = 'manager@electronica-rojas.test'");

		mockMvc.perform(get("/api/v1/branches").session(session)).andExpect(status().isUnauthorized());
	}

	/**
	 * The SPA's browser flow over real HTTP (no MockMvc csrf() shortcut, which swaps the cookie
	 * repository for a test one): read the XSRF-TOKEN cookie, echo it in X-XSRF-TOKEN, and get a
	 * new token after login; the old token stops working (SEC-002, ARCH-SEC-003).
	 */
	@Test
	void spaCsrfCookieFlowAndTokenRotationOverRealHttp() throws Exception {
		CookieManager cookies = new CookieManager();
		try (HttpClient browser = HttpClient.newBuilder().cookieHandler(cookies).build()) {
			HttpResponse<String> csrfResponse = browser.send(request("/api/v1/auth/csrf").GET().build(), ofString());
			assertThat(csrfResponse.statusCode()).isEqualTo(204);
			assertThat(setCookie(csrfResponse, "XSRF-TOKEN")).containsIgnoringCase("SameSite=Lax")
				.doesNotContainIgnoringCase("HttpOnly");
			String tokenBeforeLogin = cookie(cookies, "XSRF-TOKEN");

			HttpResponse<String> loginResponse = browser.send(request("/api/v1/auth/login")
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("X-XSRF-TOKEN", tokenBeforeLogin)
				.POST(BodyPublishers.ofString("email=" + ADMIN_EMAIL + "&password=" + ADMIN_PASSWORD))
				.build(), ofString());
			assertThat(loginResponse.statusCode()).isEqualTo(204);
			assertThat(setCookie(loginResponse, "JSESSIONID")).containsIgnoringCase("HttpOnly")
				.containsIgnoringCase("SameSite=Lax");

			assertThat(browser.send(request("/api/v1/auth/me").GET().build(), ofString()).statusCode()).isEqualTo(200);
			browser.send(request("/api/v1/auth/csrf").GET().build(), ofString());
			String tokenAfterLogin = cookie(cookies, "XSRF-TOKEN");
			assertThat(tokenAfterLogin).isNotEqualTo(tokenBeforeLogin);

			assertThat(logout(browser, tokenBeforeLogin)).as("pre-login token is rejected").isEqualTo(403);
			assertThat(logout(browser, "forged-token-value")).isEqualTo(403);
			assertThat(logout(browser, tokenAfterLogin)).isEqualTo(204);
			assertThat(browser.send(request("/api/v1/auth/me").GET().build(), ofString()).statusCode()).isEqualTo(401);
		}
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
	}

	private int logout(HttpClient browser, String csrfHeader) throws Exception {
		return browser
			.send(request("/api/v1/auth/logout").header("X-XSRF-TOKEN", csrfHeader).POST(BodyPublishers.noBody()).build(),
					ofString())
			.statusCode();
	}

	private static String setCookie(HttpResponse<?> response, String name) {
		return response.headers()
			.allValues("Set-Cookie")
			.stream()
			.filter(header -> header.startsWith(name + "="))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No Set-Cookie for " + name));
	}

	private static String cookie(CookieManager cookies, String name) {
		return cookies.getCookieStore()
			.getCookies()
			.stream()
			.filter(cookie -> cookie.getName().equals(name))
			.map(HttpCookie::getValue)
			.findFirst()
			.orElseThrow(() -> new AssertionError("No cookie " + name));
	}

	private String failedLogin(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").with(xsrf()).param("email", email).param("password", password))
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

}
