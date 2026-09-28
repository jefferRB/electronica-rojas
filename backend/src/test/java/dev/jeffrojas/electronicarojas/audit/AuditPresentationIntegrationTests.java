package dev.jeffrojas.electronicarojas.audit;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;

/**
 * Phase 3 A.2: structured details for new events, compatible reading of legacy rows (never
 * rewritten), and the action/date/branch filters with branch scope.
 */
class AuditPresentationIntegrationTests extends IntegrationTestSupport {

	private void insertLegacyEvent(String action, String entityType, Long branchId, String summary, String occurredAt) {
		jdbc.update("""
				INSERT INTO audit_events (occurred_at, actor_id, actor_name, action, entity_type, entity_id, branch_id, summary)
				VALUES (?::timestamptz, NULL, NULL, ?, ?, '1', ?, ?)
				""", occurredAt, action, entityType, branchId, summary);
	}

	@Test
	void newTransferEventsCarryStructuredDetailsWithBranchNames() throws Exception {
		long source = createBranch("SJ-01");
		long destination = createBranch("AL-01");
		long product = createProduct("CMP-100");
		setStock(source, product, 10);
		MockHttpSession admin = loginAsAdmin();
		mockMvc.perform(post("/api/v1/stock-transfers").session(admin).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"operationId":"%s","sourceBranchId":%d,"destinationBranchId":%d,"productId":%d,"quantity":4}
					""".formatted(UUID.randomUUID(), source, destination, product)))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/audit-events").param("branchId", String.valueOf(destination)).session(admin))
			.andExpect(jsonPath("$.content[0].action").value("STOCK_TRANSFER_COMPLETED"))
			.andExpect(jsonPath("$.content[0].detailsSource").value("RECORDED"))
			.andExpect(jsonPath("$.content[0].details.direction").value("RECEIVED"))
			.andExpect(jsonPath("$.content[0].details.quantity").value(4))
			.andExpect(jsonPath("$.content[0].details.sku").value("CMP-100"))
			.andExpect(jsonPath("$.content[0].details.sourceBranchName").value("Sucursal SJ-01"))
			.andExpect(jsonPath("$.content[0].details.destinationBranchName").value("Sucursal AL-01"))
			.andExpect(jsonPath("$.content[0].details.balanceAfter").value(4));
	}

	@Test
	void legacyEventsAreInterpretedWithoutBeingRewritten() throws Exception {
		long branch = createBranch("SJ-01");
		String legacy = "Transfer of 4 x CMP-100 from SJ-01 to AL-01 (sent, balance 6)";
		insertLegacyEvent("STOCK_TRANSFER_COMPLETED", "STOCK_TRANSFER", branch, legacy, "2026-09-25T16:00:00Z");
		insertLegacyEvent("PRODUCT_CREATED", "PRODUCT", null, "an unknown old format", "2026-09-25T15:00:00Z");

		mockMvc.perform(get("/api/v1/audit-events").session(loginAsAdmin()))
			.andExpect(jsonPath("$.content[0].detailsSource").value("LEGACY"))
			.andExpect(jsonPath("$.content[0].details.direction").value("SENT"))
			.andExpect(jsonPath("$.content[0].details.sourceBranchCode").value("SJ-01"))
			.andExpect(jsonPath("$.content[0].summary").value(legacy))
			.andExpect(jsonPath("$.content[1].detailsSource").value("NONE"))
			.andExpect(jsonPath("$.content[1].summary").value("an unknown old format"));

		org.assertj.core.api.Assertions
			.assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE details IS NULL", Integer.class))
			.as("history is never rewritten")
			.isEqualTo(2);
	}

	@Test
	void filtersByActionAndCostaRicaCalendarDays() throws Exception {
		long branch = createBranch("SJ-01");
		// 2026-09-26 00:30 in Costa Rica (UTC-6) is 06:30 UTC; 05:30 UTC is still the 25th locally.
		insertLegacyEvent("PRODUCT_CREATED", "PRODUCT", branch, "Product A-1 created (MERCHANDISE)", "2026-09-26T05:30:00Z");
		insertLegacyEvent("PRODUCT_CREATED", "PRODUCT", branch, "Product B-1 created (MERCHANDISE)", "2026-09-26T06:30:00Z");
		insertLegacyEvent("BRANCH_CREATED", "BRANCH", branch, "Branch SJ-01 created", "2026-09-26T07:00:00Z");
		MockHttpSession admin = loginAsAdmin();

		mockMvc.perform(get("/api/v1/audit-events").param("action", "PRODUCT_CREATED")
			.param("from", "2026-09-26")
			.param("to", "2026-09-26")
			.session(admin)).andExpect(jsonPath("$.content[*].details.sku").value(contains("B-1")));
		mockMvc.perform(get("/api/v1/audit-events").param("from", "2026-09-25").param("to", "2026-09-25").session(admin))
			.andExpect(jsonPath("$.content[*].details.sku").value(contains("A-1")));
		mockMvc.perform(get("/api/v1/audit-events").param("from", "2026-09-26").param("to", "2026-09-25").session(admin))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/audit-events").param("action", "NOT_AN_ACTION").session(admin))
			.andExpect(status().isBadRequest());
	}

	@Test
	void managersCannotReadForeignBranchesThroughAnyFilter() throws Exception {
		long own = createBranch("SJ-01");
		long foreign = createBranch("AL-01");
		insertLegacyEvent("BRANCH_CREATED", "BRANCH", own, "Branch SJ-01 created", "2026-09-26T07:00:00Z");
		insertLegacyEvent("BRANCH_CREATED", "BRANCH", foreign, "Branch AL-01 created", "2026-09-26T07:00:00Z");
		insertLegacyEvent("PRODUCT_CREATED", "PRODUCT", null, "Product X-1 created (MERCHANDISE)", "2026-09-26T07:00:00Z");
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, own);
		MockHttpSession manager = login("manager@electronica-rojas.test");

		mockMvc.perform(get("/api/v1/audit-events").session(manager))
			.andExpect(jsonPath("$.content[*].details.branchCode").value(contains("SJ-01")));
		mockMvc.perform(get("/api/v1/audit-events").param("branchId", String.valueOf(foreign))
			.param("action", "BRANCH_CREATED")
			.session(manager)).andExpect(jsonPath("$.totalElements").value(0));
	}

}
