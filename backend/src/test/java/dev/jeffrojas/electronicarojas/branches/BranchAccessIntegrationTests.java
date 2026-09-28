package dev.jeffrojas.electronicarojas.branches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;

/** E2E-02, ARCH-SEC-005, FR-BRH-001: branch scope and administration, including IDOR attempts. */
class BranchAccessIntegrationTests extends IntegrationTestSupport {

	@Test
	void adminSeesEveryBranchIncludingInactiveOnes() throws Exception {
		createBranch("SJ-01");
		createBranch("AL-01");
		createBranch("OLD-01", false);

		mockMvc.perform(get("/api/v1/branches").session(loginAsAdmin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code").value(contains("AL-01", "OLD-01", "SJ-01")));
	}

	@Test
	void managerSeesOnlyAssignedActiveBranches() throws Exception {
		long own = createBranch("SJ-01");
		createBranch("AL-01");
		long ownButInactive = createBranch("OLD-01", false);
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, own, ownButInactive);

		mockMvc.perform(get("/api/v1/branches").session(login("manager@electronica-rojas.test")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].code").value("SJ-01"));
	}

	/** Changing the ID in the URL must not reveal another branch, not even that it exists. */
	@Test
	void foreignBranchIdIsIndistinguishableFromMissingOne() throws Exception {
		long own = createBranch("SJ-01");
		long foreign = createBranch("AL-01");
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, own);
		MockHttpSession session = login("manager@electronica-rojas.test");

		mockMvc.perform(get("/api/v1/branches/{id}", own).session(session)).andExpect(status().isOk());
		String foreignBody = mockMvc.perform(get("/api/v1/branches/{id}", foreign).session(session))
			.andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String missingBody = mockMvc.perform(get("/api/v1/branches/{id}", foreign + 1000).session(session))
			.andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(foreignBody.replace("/" + foreign, "/X")).isEqualTo(missingBody.replace("/" + (foreign + 1000), "/X"));
		assertThat(foreignBody).doesNotContain("AL-01");
	}

	@Test
	void technicianOnlyReachesAssignedBranches() throws Exception {
		long own = createBranch("SJ-01");
		long foreign = createBranch("AL-01");
		createUser("tech@electronica-rojas.test", Role.TECHNICIAN, own);
		MockHttpSession session = login("tech@electronica-rojas.test");

		mockMvc.perform(get("/api/v1/branches").session(session)).andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(get("/api/v1/branches/{id}", foreign).session(session)).andExpect(status().isNotFound());
	}

	@Test
	void removingAnAssignmentTakesEffectWithoutLoggingOut() throws Exception {
		long own = createBranch("SJ-01");
		long userId = createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, own);
		MockHttpSession session = login("manager@electronica-rojas.test");
		mockMvc.perform(get("/api/v1/branches/{id}", own).session(session)).andExpect(status().isOk());

		jdbc.update("DELETE FROM user_branches WHERE user_id = ?", userId);

		mockMvc.perform(get("/api/v1/branches/{id}", own).session(session)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/auth/me").session(session)).andExpect(jsonPath("$.branches.length()").value(0));
	}

	@Test
	void deactivatedBranchDisappearsForAssignedCollaborators() throws Exception {
		long own = createBranch("SJ-01");
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, own);
		MockHttpSession session = login("manager@electronica-rojas.test");

		jdbc.update("UPDATE branches SET active = FALSE WHERE id = ?", own);

		mockMvc.perform(get("/api/v1/branches/{id}", own).session(session)).andExpect(status().isNotFound());
	}

	@Test
	void onlyAdminsCreateBranches() throws Exception {
		long own = createBranch("SJ-01");
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, own);
		String body = """
				{"code":"he-01","name":"Heredia Centro","address":"Heredia"}
				""";

		mockMvc.perform(post("/api/v1/branches").with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/branches").session(login("manager@electronica-rojas.test")).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isForbidden());
		MockHttpSession admin = loginAsAdmin();
		mockMvc.perform(post("/api/v1/branches").session(admin).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/branches").session(admin).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("HE-01"))
			.andExpect(jsonPath("$.active").value(true));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM branches", Integer.class)).isEqualTo(2);
	}

	@Test
	void branchCodesAreUniqueIgnoringCase() throws Exception {
		createBranch("SJ-01");

		mockMvc.perform(post("/api/v1/branches").session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"sj-01\",\"name\":\"Duplicada\"}")).andExpect(status().isConflict());
	}

	@Test
	void invalidBranchPayloadReturnsFieldErrors() throws Exception {
		mockMvc.perform(post("/api/v1/branches").session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"x\",\"name\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("code", "name")));
	}

	@Test
	void updateUsesOptimisticVersionAndCanDeactivate() throws Exception {
		long branchId = createBranch("SJ-01");
		MockHttpSession admin = loginAsAdmin();
		String update = """
				{"name":"San José Centro","address":"Av. 2","active":false,"version":%d}
				""";

		mockMvc.perform(put("/api/v1/branches/{id}", branchId).session(admin).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(0)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false))
			.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(put("/api/v1/branches/{id}", branchId).session(admin).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(0))).andExpect(status().isConflict());
	}

	@Test
	void nonNumericIdIsABadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/branches/abc").session(loginAsAdmin())).andExpect(status().isBadRequest());
	}

}
