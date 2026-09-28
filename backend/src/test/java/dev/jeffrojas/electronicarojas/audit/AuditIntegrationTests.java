package dev.jeffrojas.electronicarojas.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;

/** OBS-001/002, BR-SEC-002, FR-AUD-001: who did what and when, scoped and without secrets. */
class AuditIntegrationTests extends IntegrationTestSupport {

	private static final String REQUEST_ID = "test-correlation-0001";

	@Test
	void stockMovementIsAuditedWithActorBranchOperationAndCorrelationId() throws Exception {
		long branch = createBranch("SJ-01");
		long product = createProduct("CMP-100");
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, branch);
		UUID operation = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/stock-movements").session(login("manager@electronica-rojas.test")).with(xsrf())
			.header("X-Request-Id", REQUEST_ID)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"operationId":"%s","branchId":%d,"productId":%d,"type":"RECEIPT","quantity":5,"reason":"Compra"}
					""".formatted(operation, branch, product)))
			.andExpect(status().isCreated())
			.andExpect(header().string("X-Request-Id", REQUEST_ID));

		Map<String, Object> event = jdbc.queryForMap("SELECT * FROM audit_events WHERE action = 'STOCK_MOVEMENT_RECORDED'");
		assertThat(event).containsEntry("entity_type", "STOCK_MOVEMENT")
			.containsEntry("branch_id", branch)
			.containsEntry("operation_id", operation)
			.containsEntry("correlation_id", REQUEST_ID)
			.containsEntry("actor_name", "Usuario BRANCH_MANAGER");
		assertThat((String) event.get("summary")).contains("RECEIPT", "CMP-100", "SJ-01", "0 -> 5");
	}

	@Test
	void malformedCorrelationIdsAreReplacedToPreventLogInjection() throws Exception {
		mockMvc.perform(get("/api/v1/ping").header("X-Request-Id", "bad\nvalue"))
			.andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")));
	}

	@Test
	void administrativeActionsAreAuditedWithoutSecrets() throws Exception {
		long branch = createBranch("SJ-01");
		long userId = createUser("tech@electronica-rojas.test", Role.TECHNICIAN, branch);
		long version = jdbc.queryForObject("SELECT version FROM app_users WHERE id = ?", Long.class, userId);

		mockMvc.perform(post("/api/v1/users/{id}/password-reset", userId).session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"newPassword\":\"brand-new-password-1\",\"version\":" + version + "}"))
			.andExpect(status().isNoContent());

		List<String> summaries = jdbc.queryForList("SELECT summary FROM audit_events", String.class);
		assertThat(jdbc.queryForObject("SELECT action FROM audit_events", String.class)).isEqualTo("USER_PASSWORD_RESET");
		assertThat(summaries).allSatisfy(summary -> assertThat(summary).doesNotContain("brand-new-password-1")
			.doesNotContain("{bcrypt}"));
	}

	@Test
	void failedOperationsLeaveNoAuditTrail() throws Exception {
		long branch = createBranch("SJ-01");
		long product = createProduct("CMP-100");

		mockMvc.perform(post("/api/v1/stock-movements").session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"operationId":"%s","branchId":%d,"productId":%d,"type":"ISSUE","quantity":1,"reason":"Venta"}
					""".formatted(UUID.randomUUID(), branch, product)))
			.andExpect(status().isConflict());

		assertThat(countRows("audit_events")).isZero();
	}

	@Test
	void managersReadOnlyTheirBranchesAndOtherRolesNothing() throws Exception {
		long own = createBranch("SJ-01");
		long foreign = createBranch("AL-01");
		long product = createProduct("CMP-100");
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, own);
		createUser("recep@electronica-rojas.test", Role.RECEPTIONIST, own);
		MockHttpSession admin = loginAsAdmin();
		for (long branch : new long[] { own, foreign }) {
			mockMvc.perform(post("/api/v1/stock-movements").session(admin).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"operationId":"%s","branchId":%d,"productId":%d,"type":"RECEIPT","quantity":1,"reason":"Compra"}
						""".formatted(UUID.randomUUID(), branch, product)))
				.andExpect(status().isCreated());
		}

		mockMvc.perform(get("/api/v1/audit-events").session(login("manager@electronica-rojas.test")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[*].branchId").value(everyItem(is((int) own))));
		mockMvc.perform(get("/api/v1/audit-events").param("branchId", String.valueOf(foreign))
			.session(login("manager@electronica-rojas.test"))).andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/v1/audit-events").param("entityType", "STOCK_MOVEMENT").session(admin))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[*].entityType").value(contains("STOCK_MOVEMENT", "STOCK_MOVEMENT")));
		mockMvc.perform(get("/api/v1/audit-events").session(login("recep@electronica-rojas.test")))
			.andExpect(status().isForbidden());
	}

}
