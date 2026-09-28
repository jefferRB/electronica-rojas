package dev.jeffrojas.electronicarojas.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;

/** FR-AUTH-004, FR-BRH-002, BR-BRH-002..004. */
class UserAdministrationIntegrationTests extends IntegrationTestSupport {

	private static final String NEW_PASSWORD = "brand-new-password-1";

	@Test
	void adminCreatesCollaboratorWhoCanLogInWithScopedBranches() throws Exception {
		long branch = createBranch("SJ-01");
		createBranch("AL-01");

		String body = mockMvc
			.perform(post("/api/v1/users").session(loginAsAdmin()).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createUserJson("Nueva@Electronica-Rojas.test", "BRANCH_MANAGER", NEW_PASSWORD, branch)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("nueva@electronica-rojas.test"))
			.andExpect(jsonPath("$.branches[0].code").value("SJ-01"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContainIgnoringCase("password").doesNotContain("{bcrypt}");

		MockHttpSession session = login("nueva@electronica-rojas.test", NEW_PASSWORD);
		mockMvc.perform(get("/api/v1/auth/me").session(session))
			.andExpect(jsonPath("$.user.role").value("BRANCH_MANAGER"))
			.andExpect(jsonPath("$.branches.length()").value(1));
	}

	@Test
	void passwordsAreStoredAsAdaptiveHashes() throws Exception {
		long branch = createBranch("SJ-01");
		mockMvc.perform(post("/api/v1/users").session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(createUserJson("hash@electronica-rojas.test", "RECEPTIONIST", NEW_PASSWORD, branch)))
			.andExpect(status().isCreated());

		String stored = jdbc.queryForObject("SELECT password_hash FROM app_users WHERE email = 'hash@electronica-rojas.test'",
				String.class);
		assertThat(stored).startsWith("{bcrypt}$2").doesNotContain(NEW_PASSWORD);
	}

	@Test
	void onlyAdminsManageUsers() throws Exception {
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, createBranch("SJ-01"));
		MockHttpSession manager = login("manager@electronica-rojas.test");

		mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/users").session(manager)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/users").session(manager).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(createUserJson("x@electronica-rojas.test", "ADMIN", NEW_PASSWORD))).andExpect(status().isForbidden());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users WHERE email = 'x@electronica-rojas.test'", Integer.class))
			.isZero();
	}

	@Test
	void duplicateEmailIgnoringCaseIsAConflict() throws Exception {
		createUser("dup@electronica-rojas.test", Role.TECHNICIAN, createBranch("SJ-01"));

		mockMvc.perform(post("/api/v1/users").session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(createUserJson("DUP@electronica-rojas.test", "ADMIN", NEW_PASSWORD))).andExpect(status().isConflict());
	}

	@Test
	void nonAdminRolesNeedAtLeastOneActiveBranch() throws Exception {
		long inactive = createBranch("OLD-01", false);
		MockHttpSession admin = loginAsAdmin();

		mockMvc.perform(post("/api/v1/users").session(admin).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(createUserJson("a@electronica-rojas.test", "TECHNICIAN", NEW_PASSWORD)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("branchIds"));
		mockMvc.perform(post("/api/v1/users").session(admin).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(createUserJson("b@electronica-rojas.test", "TECHNICIAN", NEW_PASSWORD, inactive, 999_999)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void passwordPolicyEndpointDescribesExactlyTheEnforcedRules() throws Exception {
		mockMvc.perform(get("/api/v1/auth/password-policy").session(loginAsAdmin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.minLength").value(PasswordPolicy.MIN_LENGTH))
			.andExpect(jsonPath("$.maxBytes").value(PasswordPolicy.MAX_BYTES));
		mockMvc.perform(get("/api/v1/auth/password-policy")).andExpect(status().isUnauthorized());
	}

	@Test
	void weakPasswordsAreRejected() throws Exception {
		mockMvc.perform(post("/api/v1/users").session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(createUserJson("weak@electronica-rojas.test", "ADMIN", "short")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("password"))
			.andExpect(jsonPath("$.errors[0].code").value("PASSWORD_TOO_SHORT"));
	}

	@Test
	void theLastActiveAdministratorCannotBeDemotedOrDeactivated() throws Exception {
		long adminId = jdbc.queryForObject("SELECT id FROM app_users WHERE email = ?", Long.class, ADMIN_EMAIL);
		long branch = createBranch("SJ-01");
		MockHttpSession admin = loginAsAdmin();

		mockMvc.perform(put("/api/v1/users/{id}", adminId).session(admin).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(updateUserJson("Admin", "BRANCH_MANAGER", true, version(adminId), branch)))
			.andExpect(status().isConflict());
		mockMvc.perform(put("/api/v1/users/{id}", adminId).session(admin).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(updateUserJson("Admin", "ADMIN", false, version(adminId))))
			.andExpect(status().isConflict());
	}

	@Test
	void anAdminCanBeDemotedWhenAnotherActiveAdminExists() throws Exception {
		long otherAdmin = createUser("admin2@electronica-rojas.test", Role.ADMIN);
		long branch = createBranch("SJ-01");

		mockMvc.perform(put("/api/v1/users/{id}", otherAdmin).session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(updateUserJson("Ex admin", "BRANCH_MANAGER", true, version(otherAdmin), branch)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("BRANCH_MANAGER"))
			.andExpect(jsonPath("$.branches[0].code").value("SJ-01"));
	}

	@Test
	void staleVersionIsAConflict() throws Exception {
		long branch = createBranch("SJ-01");
		long userId = createUser("tech@electronica-rojas.test", Role.TECHNICIAN, branch);

		mockMvc.perform(put("/api/v1/users/{id}", userId).session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(updateUserJson("Tech", "TECHNICIAN", true, version(userId) + 5, branch)))
			.andExpect(status().isConflict());
	}

	@Test
	void roleChangeByAdminRevokesTheOpenSessionOfThatUser() throws Exception {
		long branch = createBranch("SJ-01");
		long userId = createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, branch);
		MockHttpSession managerSession = login("manager@electronica-rojas.test");

		mockMvc.perform(put("/api/v1/users/{id}", userId).session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(updateUserJson("Manager", "RECEPTIONIST", true, version(userId), branch)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/auth/me").session(managerSession)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/auth/me").session(login("manager@electronica-rojas.test")))
			.andExpect(jsonPath("$.user.role").value("RECEPTIONIST"));
	}

	@Test
	void passwordResetRevokesSessionsAndTheOldPassword() throws Exception {
		long userId = createUser("tech@electronica-rojas.test", Role.TECHNICIAN, createBranch("SJ-01"));
		MockHttpSession oldSession = login("tech@electronica-rojas.test");

		mockMvc.perform(post("/api/v1/users/{id}/password-reset", userId).session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"newPassword\":\"" + NEW_PASSWORD + "\",\"version\":" + version(userId) + "}"))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/auth/me").session(oldSession)).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/auth/login").with(xsrf()).param("email", "tech@electronica-rojas.test")
			.param("password", USER_PASSWORD)).andExpect(status().isUnauthorized());
		login("tech@electronica-rojas.test", NEW_PASSWORD);
	}

	@Test
	void userListIsPaginatedWithAnUpperBound() throws Exception {
		long branch = createBranch("SJ-01");
		createUser("aa@electronica-rojas.test", Role.TECHNICIAN, branch);
		createUser("b@electronica-rojas.test", Role.TECHNICIAN, branch);
		MockHttpSession admin = loginAsAdmin();

		mockMvc.perform(get("/api/v1/users").param("size", "2").session(admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.content[0].email").value("aa@electronica-rojas.test"))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(2));
		mockMvc.perform(get("/api/v1/users").param("size", "500").session(admin)).andExpect(status().isBadRequest());
	}

	private long version(long userId) {
		return jdbc.queryForObject("SELECT version FROM app_users WHERE id = ?", Long.class, userId);
	}

	private static String createUserJson(String email, String role, String password, long... branchIds) {
		return """
				{"email":"%s","fullName":"Colaborador de prueba","role":"%s","password":"%s","branchIds":%s}
				""".formatted(email, role, password, ids(branchIds));
	}

	private static String updateUserJson(String fullName, String role, boolean active, long version, long... branchIds) {
		return """
				{"fullName":"%s","role":"%s","active":%s,"branchIds":%s,"version":%d}
				""".formatted(fullName, role, active, ids(branchIds), version);
	}

	private static String ids(long... ids) {
		return Arrays.toString(ids);
	}

}
