package dev.jeffrojas.electronicarojas.repairs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import dev.jeffrojas.electronicarojas.BrowserClient;
import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;
import com.jayway.jsonpath.JsonPath;

/**
 * FR-REP-001..005 / BR-REP-001..006 (B.7): reception, several orders per customer, branch scope,
 * the state machine through HTTP, quotes, immutable history, no double delivery, no partial records
 * and audit without personal data. Concurrent delivery is in RepairConcurrencyIntegrationTests.
 */
class RepairOrderIntegrationTests extends IntegrationTestSupport {

	@LocalServerPort
	private int port;

	private long sanJose;

	private long alajuela;

	private long technicianId;

	private long otherTechnicianId;

	private MockHttpSession manager;

	private MockHttpSession receptionist;

	private MockHttpSession technician;

	private MockHttpSession otherTechnician;

	private MockHttpSession alajuelaReceptionist;

	@BeforeEach
	void setUp() throws Exception {
		sanJose = createBranch("SJ-01");
		alajuela = createBranch("AL-01");
		createUser("gerente@electronica-rojas.test", Role.BRANCH_MANAGER, sanJose);
		createUser("recepcion@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		technicianId = createUser("tecnico@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		otherTechnicianId = createUser("tecnico2@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		createUser("recepcion.al@electronica-rojas.test", Role.RECEPTIONIST, alajuela);
		manager = login("gerente@electronica-rojas.test");
		receptionist = login("recepcion@electronica-rojas.test");
		technician = login("tecnico@electronica-rojas.test");
		otherTechnician = login("tecnico2@electronica-rojas.test");
		alajuelaReceptionist = login("recepcion.al@electronica-rojas.test");
	}

	// ---- helpers ----

	private static String newCustomerOrder(UUID operationId, long branchId, String phone) {
		return newCustomerOrder(operationId, branchId, "Ana Pérez", phone);
	}

	private static String newCustomerOrder(UUID operationId, long branchId, String name, String phone) {
		return """
				{"operationId":"%s","branchId":%d,
				 "newCustomer":{"fullName":"%s","phone":"%s","email":"ana@ejemplo.test"},
				 "deviceType":"Televisor","brand":"Samsung","model":"UN55","serialNumber":"SN-123",
				 "reportedFault":"No enciende","physicalCondition":"Rayón en el marco","accessories":"Control remoto"}
				""".formatted(operationId, branchId, name, phone);
	}

	private static String existingCustomerOrder(long branchId, long customerId, String phoneProof) {
		return """
				{"operationId":"%s","branchId":%d,"customerId":%d,%s
				 "deviceType":"Microondas","brand":"LG","reportedFault":"No calienta","physicalCondition":"Bueno"}
				""".formatted(UUID.randomUUID(), branchId, customerId,
				phoneProof == null ? "" : "\"customerPhone\":\"" + phoneProof + "\",");
	}

	private ResultActions receive(MockHttpSession session, String json) throws Exception {
		return mockMvc.perform(post("/api/v1/repair-orders").session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private Map<String, Object> receiveNew(String phone) throws Exception {
		return receiveNew("Ana Pérez", phone);
	}

	private Map<String, Object> receiveNew(String name, String phone) throws Exception {
		String body = receive(receptionist, newCustomerOrder(UUID.randomUUID(), sanJose, name, phone))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$");
	}

	private static long id(Map<String, Object> order) {
		return ((Number) order.get("id")).longValue();
	}

	private static long customerId(Map<String, Object> order) {
		return ((Number) JsonPath.read(order, "$.customer.id")).longValue();
	}

	private ResultActions transition(MockHttpSession session, long orderId, String toStatus, String reason)
			throws Exception {
		String json = reason == null ? "{\"toStatus\":\"%s\"}".formatted(toStatus)
				: "{\"toStatus\":\"%s\",\"reason\":\"%s\"}".formatted(toStatus, reason);
		return mockMvc.perform(post("/api/v1/repair-orders/{id}/status", orderId).session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private ResultActions assign(MockHttpSession session, long orderId, long userId) throws Exception {
		return mockMvc.perform(put("/api/v1/repair-orders/{id}/technician", orderId).session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"technicianId\":%d}".formatted(userId)));
	}

	private ResultActions createQuote(MockHttpSession session, long orderId, String amount) throws Exception {
		return mockMvc.perform(post("/api/v1/repair-orders/{id}/quotes", orderId).session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"amount\":%s,\"description\":\"Cambio de fuente de poder\"}".formatted(amount)));
	}

	private ResultActions decide(MockHttpSession session, long orderId, long quoteId, String decision)
			throws Exception {
		return mockMvc.perform(post("/api/v1/repair-orders/{id}/quotes/{q}/decision", orderId, quoteId).session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"decision\":\"%s\",\"method\":\"PHONE\",\"note\":\"Llamada\"}".formatted(decision)));
	}

	private long quoteIdOf(long orderId) {
		return jdbc.queryForObject("SELECT max(id) FROM repair_quotes WHERE order_id = ?", Long.class, orderId);
	}

	private int historyCount(long orderId) {
		return jdbc.queryForObject("SELECT count(*) FROM repair_status_history WHERE order_id = ?", Integer.class,
				orderId);
	}

	private String statusOf(long orderId) {
		return jdbc.queryForObject("SELECT status FROM repair_orders WHERE id = ?", String.class, orderId);
	}

	/** Order assigned to the technician and in DIAGNOSING. */
	private long diagnosingOrder() throws Exception {
		long orderId = id(receiveNew("8888-7777"));
		assign(manager, orderId, technicianId).andExpect(status().isOk());
		transition(technician, orderId, "DIAGNOSING", null).andExpect(status().isOk());
		return orderId;
	}

	// ---- reception ----

	@Test
	void receptionCreatesCustomerOrderHistoryAndAuditTogether() throws Exception {
		Map<String, Object> order = receiveNew("8888-7777");
		long orderId = id(order);

		assertThat((String) order.get("orderCode")).matches("OR-\\d{4}-\\d{6}");
		assertThat(order.get("status")).isEqualTo("RECEIVED");
		assertThat(order.get("inCustody")).isEqualTo(true);
		assertThat((String) JsonPath.read(order, "$.customer.phone")).isEqualTo("+50688887777");
		assertThat((String) JsonPath.read(order, "$.receivedBy.fullName")).isEqualTo("Usuario RECEPTIONIST");
		assertThat((List<?>) JsonPath.read(order, "$.history")).hasSize(1);
		assertThat((Object) JsonPath.read(order, "$.history[0].fromStatus")).isNull();
		assertThat((List<String>) JsonPath.read(order, "$.actions.transitions[*].toStatus")).containsExactly("CANCELLED");

		assertThat(countRows("customers")).isEqualTo(1);
		assertThat(historyCount(orderId)).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT action FROM audit_events ORDER BY id", String.class))
			.containsExactly("CUSTOMER_CREATED", "REPAIR_ORDER_RECEIVED");
		// Audit keeps codes and snapshots, never the customer's personal data or the fault text.
		assertThat(jdbc.queryForList("SELECT summary || ' ' || coalesce(details::text, '') FROM audit_events",
				String.class))
			.allSatisfy(text -> assertThat(text).doesNotContain("Ana", "8888", "ejemplo", "No enciende"));
	}

	@Test
	void doubleSubmitCreatesASingleOrder() throws Exception {
		UUID operationId = UUID.randomUUID();
		String json = newCustomerOrder(operationId, sanJose, "8888-7777");
		String first = receive(receptionist, json).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String second = receive(receptionist, json).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		assertThat((Object) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
		assertThat(countRows("repair_orders")).isEqualTo(1);
		assertThat(countRows("customers")).isEqualTo(1);

		receive(receptionist, newCustomerOrder(operationId, sanJose, "7000-1234")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OPERATION_ID_REUSED"));
	}

	@Test
	void oneCustomerHasManyOrdersEachWithItsOwnBranch() throws Exception {
		long customer = customerId(receiveNew("8888-7777"));
		receive(receptionist, existingCustomerOrder(sanJose, customer, null)).andExpect(status().isCreated());

		// Another branch may serve the same customer only by proving the phone on file.
		receive(alajuelaReceptionist, existingCustomerOrder(alajuela, customer, null)).andExpect(status().isNotFound());
		receive(alajuelaReceptionist, existingCustomerOrder(alajuela, customer, "7000-0000"))
			.andExpect(status().isNotFound());
		receive(alajuelaReceptionist, existingCustomerOrder(alajuela, customer, "+506 8888 7777"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.branch.code").value("AL-01"))
			.andExpect(jsonPath("$.customer.id").value(customer));

		assertThat(countRows("customers")).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT b.code FROM repair_orders o JOIN branches b ON b.id = o.branch_id "
				+ "WHERE o.customer_id = ? ORDER BY o.id", String.class, customer))
			.containsExactly("SJ-01", "SJ-01", "AL-01");
		// The customer's repair history as each branch sees it.
		mockMvc.perform(get("/api/v1/repair-orders").param("customerId", String.valueOf(customer)).session(manager))
			.andExpect(jsonPath("$.totalElements").value(2));
		mockMvc.perform(get("/api/v1/repair-orders").param("customerId", String.valueOf(customer)).session(loginAsAdmin()))
			.andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	void receptionNeedsExactlyOneCustomerSource() throws Exception {
		receive(receptionist, """
				{"operationId":"%s","branchId":%d,"deviceType":"Radio","brand":"Sony","reportedFault":"x","physicalCondition":"y"}
				""".formatted(UUID.randomUUID(), sanJose))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("CUSTOMER_REQUIRED"));
		receive(alajuelaReceptionist, newCustomerOrder(UUID.randomUUID(), sanJose, "8888-7777"))
			.andExpect(status().isNotFound());
		receive(technician, newCustomerOrder(UUID.randomUUID(), sanJose, "8888-7777")).andExpect(status().isForbidden());
		assertThat(countRows("repair_orders") + countRows("customers") + countRows("audit_events")).isZero();
	}

	/** TST-004: a failure after the customer and the order were inserted leaves nothing behind. */
	@Test
	void aFailureInTheMiddleOfReceptionRollsBackEverything() throws Exception {
		jdbc.execute("""
				CREATE FUNCTION test_fail_history() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN RAISE EXCEPTION 'simulated failure'; END; $$
				""");
		jdbc.execute("""
				CREATE TRIGGER test_fail_history BEFORE INSERT ON repair_status_history
				FOR EACH ROW EXECUTE FUNCTION test_fail_history()
				""");
		try (BrowserClient browser = BrowserClient.login(port, "recepcion@electronica-rojas.test", USER_PASSWORD)) {
			BrowserClient.Response response = browser.postJson("/api/v1/repair-orders",
					newCustomerOrder(UUID.randomUUID(), sanJose, "8888-7777"));
			assertThat(response.status()).isEqualTo(500);
		}
		finally {
			jdbc.execute("DROP TRIGGER test_fail_history ON repair_status_history");
			jdbc.execute("DROP FUNCTION test_fail_history()");
		}
		assertThat(countRows("customers") + countRows("repair_orders") + countRows("repair_status_history")
				+ countRows("audit_events")).isZero();
	}

	// ---- scope ----

	@Test
	void otherBranchesAndUnassignedTechniciansGetTheSame404() throws Exception {
		long orderId = id(receiveNew("8888-7777"));

		mockMvc.perform(get("/api/v1/repair-orders/{id}", orderId).session(alajuelaReceptionist))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/repair-orders/{id}", 999_999).session(alajuelaReceptionist))
			.andExpect(status().isNotFound());
		transition(alajuelaReceptionist, orderId, "CANCELLED", "x").andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/repair-orders").session(alajuelaReceptionist))
			.andExpect(jsonPath("$.totalElements").value(0));

		assign(manager, orderId, technicianId).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/repair-orders/{id}", orderId).session(otherTechnician))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/repair-orders").session(otherTechnician))
			.andExpect(jsonPath("$.totalElements").value(0));
		assertThat(statusOf(orderId)).isEqualTo("RECEIVED");
	}

	@Test
	void assignedTechnicianSeesTheOrderButNotTheCustomerContact() throws Exception {
		long orderId = id(receiveNew("8888-7777"));
		assign(manager, orderId, technicianId).andExpect(status().isOk())
			.andExpect(jsonPath("$.technician.id").value(technicianId));

		mockMvc.perform(get("/api/v1/repair-orders/{id}", orderId).session(technician))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.customer.fullName").value("Ana Pérez"))
			.andExpect(jsonPath("$.customer.phone").doesNotExist())
			.andExpect(jsonPath("$.customer.email").doesNotExist())
			.andExpect(jsonPath("$.actions.transitions[*].toStatus").value(contains("DIAGNOSING")));
		mockMvc.perform(get("/api/v1/repair-orders").session(technician)).andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void onlyEligibleTechniciansCanBeAssigned() throws Exception {
		long orderId = id(receiveNew("8888-7777"));
		long alajuelaTechnician = createUser("tecnico.al@electronica-rojas.test", Role.TECHNICIAN, alajuela);

		assign(manager, orderId, alajuelaTechnician).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("TECHNICIAN_NOT_ELIGIBLE"));
		assign(receptionist, orderId, technicianId).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/repair-orders/technicians").param("branchId", String.valueOf(sanJose))
			.session(manager))
			.andExpect(jsonPath("$[*].id").value(containsInAnyOrder((int) technicianId, (int) otherTechnicianId)));
		mockMvc.perform(get("/api/v1/repair-orders/technicians").param("branchId", String.valueOf(alajuela))
			.session(manager)).andExpect(status().isNotFound());
	}

	// ---- state machine ----

	@Test
	void invalidTransitionLeavesStatusAndHistoryUntouched() throws Exception {
		long orderId = id(receiveNew("8888-7777"));

		transition(manager, orderId, "READY_FOR_PICKUP", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
			.andExpect(jsonPath("$.fromStatus").value("RECEIVED"))
			.andExpect(jsonPath("$.toStatus").value("READY_FOR_PICKUP"));
		transition(manager, orderId, "DIAGNOSING", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TECHNICIAN_REQUIRED"));
		transition(manager, orderId, "CANCELLED", "  ").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("REASON_REQUIRED"));

		assertThat(statusOf(orderId)).isEqualTo("RECEIVED");
		assertThat(historyCount(orderId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'REPAIR_STATUS_CHANGED'",
				Integer.class)).isZero();
	}

	@Test
	void quoteStatusesCannotBeForcedThroughTheStatusEndpoint() throws Exception {
		long orderId = diagnosingOrder();
		transition(manager, orderId, "AWAITING_APPROVAL", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
		createQuote(technician, orderId, "25000").andExpect(status().isOk());
		transition(manager, orderId, "APPROVED", null).andExpect(status().isConflict());
		assertThat(statusOf(orderId)).isEqualTo("AWAITING_APPROVAL");
	}

	@Test
	void rolesArePerAction() throws Exception {
		long orderId = id(receiveNew("8888-7777"));
		assign(manager, orderId, technicianId);

		transition(receptionist, orderId, "DIAGNOSING", null).andExpect(status().isForbidden());
		transition(technician, orderId, "DIAGNOSING", null).andExpect(status().isOk());
		transition(technician, orderId, "CANCELLED", "No quiere").andExpect(status().isForbidden());
		assertThat(historyCount(orderId)).isEqualTo(2);
	}

	@Test
	void fullRepairFlowRecordsEveryStepAndDeliversOnlyOnce() throws Exception {
		long orderId = diagnosingOrder();
		mockMvc.perform(put("/api/v1/repair-orders/{id}/diagnosis", orderId).session(technician)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"diagnosis\":\"Fuente de poder dañada\",\"version\":%d}".formatted(
					jdbc.queryForObject("SELECT version FROM repair_orders WHERE id = ?", Long.class, orderId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.diagnosis").value("Fuente de poder dañada"))
			.andExpect(jsonPath("$.diagnosisUpdatedBy.id").value(technicianId));

		createQuote(technician, orderId, "25000.50").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
			.andExpect(jsonPath("$.quotes[0].amount").value(25000.50))
			.andExpect(jsonPath("$.quotes[0].currency").value("CRC"))
			.andExpect(jsonPath("$.quotes[0].status").value("PENDING"))
			.andExpect(jsonPath("$.actions.canCreateQuote").value(false));
		decide(technician, orderId, quoteIdOf(orderId), "APPROVED").andExpect(status().isForbidden());
		decide(receptionist, orderId, quoteIdOf(orderId), "APPROVED").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.quotes[0].decisionMethod").value("PHONE"))
			.andExpect(jsonPath("$.quotes[0].decidedBy.fullName").value("Usuario RECEPTIONIST"));
		transition(technician, orderId, "IN_REPAIR", null).andExpect(status().isOk());
		transition(technician, orderId, "READY_FOR_PICKUP", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.resolution").value("REPAIRED"));
		transition(receptionist, orderId, "DELIVERED", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.inCustody").value(false))
			.andExpect(jsonPath("$.deliveredBy.fullName").value("Usuario RECEPTIONIST"))
			.andExpect(jsonPath("$.actions.transitions", hasSize(0)));

		transition(manager, orderId, "DELIVERED", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

		mockMvc.perform(get("/api/v1/repair-orders/{id}", orderId).session(manager))
			.andExpect(jsonPath("$.history[*].toStatus").value(contains("RECEIVED", "DIAGNOSING", "AWAITING_APPROVAL",
					"APPROVED", "IN_REPAIR", "READY_FOR_PICKUP", "DELIVERED")))
			.andExpect(jsonPath("$.history[*].fromStatus").value(contains(null, "RECEIVED", "DIAGNOSING",
					"AWAITING_APPROVAL", "APPROVED", "IN_REPAIR", "READY_FOR_PICKUP")))
			.andExpect(jsonPath("$.history[6].actor.fullName").value("Usuario RECEPTIONIST"))
			.andExpect(jsonPath("$.history[1].actor.id").value(technicianId));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM repair_status_history WHERE to_status = 'DELIVERED'",
				Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT action FROM audit_events WHERE entity_type <> 'CUSTOMER' ORDER BY id",
				String.class))
			.containsExactly("REPAIR_ORDER_RECEIVED", "REPAIR_TECHNICIAN_ASSIGNED", "REPAIR_STATUS_CHANGED",
					"REPAIR_DIAGNOSIS_UPDATED", "REPAIR_STATUS_CHANGED", "REPAIR_QUOTE_CREATED", "REPAIR_STATUS_CHANGED",
					"REPAIR_QUOTE_DECIDED", "REPAIR_STATUS_CHANGED", "REPAIR_STATUS_CHANGED", "REPAIR_STATUS_CHANGED");
		assertThat(jdbc.queryForList("SELECT coalesce(details::text, '') FROM audit_events", String.class))
			.allSatisfy(text -> assertThat(text).doesNotContain("Fuente de poder", "Ana", "8888"));
	}

	@Test
	void staleDiagnosisIsRejected() throws Exception {
		long orderId = diagnosingOrder();
		mockMvc.perform(put("/api/v1/repair-orders/{id}/diagnosis", orderId).session(technician)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"diagnosis\":\"x\",\"version\":0}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"));
	}

	@Test
	void rejectedQuoteCancelsTheOrderAndCanNeverBeApproved() throws Exception {
		long orderId = diagnosingOrder();
		createQuote(technician, orderId, "90000").andExpect(status().isOk());
		long quoteId = quoteIdOf(orderId);

		decide(receptionist, orderId, quoteId, "REJECTED").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.resolution").value("CANCELLED"))
			.andExpect(jsonPath("$.history[3].reason").value(RepairQuoteService.REJECTION_REASON));
		decide(manager, orderId, quoteId, "APPROVED").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("QUOTE_ALREADY_DECIDED"));
		assertThat(jdbc.queryForObject("SELECT status FROM repair_quotes WHERE id = ?", String.class, quoteId))
			.isEqualTo("REJECTED");
		// Not even a direct UPDATE can turn it into approved.
		assertThatThrownBy(() -> jdbc.update("UPDATE repair_quotes SET status = 'APPROVED' WHERE id = ?", quoteId))
			.isInstanceOf(DataAccessException.class);

		// The unrepaired appliance still has to be handed back: CANCELLED -> DELIVERED.
		transition(receptionist, orderId, "DELIVERED", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.resolution").value("CANCELLED"))
			.andExpect(jsonPath("$.inCustody").value(false));
	}

	@Test
	void onlyOnePendingQuotePerOrder() throws Exception {
		long orderId = diagnosingOrder();
		createQuote(technician, orderId, "1000").andExpect(status().isOk());
		createQuote(manager, orderId, "2000").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("QUOTE_PENDING_EXISTS"));
		createQuote(technician, orderId, "0").andExpect(status().isBadRequest());
		assertThat(countRows("repair_quotes")).isEqualTo(1);
	}

	@Test
	void unrepairableNeedsAReasonAndIsReturnedToTheCustomer() throws Exception {
		long orderId = diagnosingOrder();
		transition(technician, orderId, "UNREPAIRABLE", null).andExpect(status().isBadRequest());
		transition(technician, orderId, "UNREPAIRABLE", "Placa sin repuesto").andExpect(status().isOk())
			.andExpect(jsonPath("$.resolution").value("UNREPAIRABLE"))
			.andExpect(jsonPath("$.inCustody").value(true));
		transition(receptionist, orderId, "DELIVERED", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DELIVERED"))
			.andExpect(jsonPath("$.resolution").value("UNREPAIRABLE"));
	}

	@Test
	void historyAndOrdersCannotBeDeletedOrRewritten() throws Exception {
		long orderId = id(receiveNew("8888-7777"));

		assertThatThrownBy(() -> jdbc.update("UPDATE repair_status_history SET reason = 'x' WHERE order_id = ?", orderId))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbc.update("DELETE FROM repair_status_history WHERE order_id = ?", orderId))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbc.update("DELETE FROM repair_orders WHERE id = ?", orderId))
			.isInstanceOf(DataAccessException.class);
		assertThat(historyCount(orderId)).isEqualTo(1);
	}

	@Test
	void listFiltersByStatusAndSearch() throws Exception {
		long first = id(receiveNew("8888-7777"));
		receive(receptionist, existingCustomerOrder(sanJose, customerId(receiveNew("Carlos Mora", "7000-1234")), null));
		transition(receptionist, first, "CANCELLED", "El cliente desistió").andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/repair-orders").param("status", "CANCELLED").session(manager))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(first));
		mockMvc.perform(get("/api/v1/repair-orders").param("search", "lg").session(manager))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].device.brand").value("LG"));
		mockMvc.perform(get("/api/v1/repair-orders").param("from", "2026-02-01").param("to", "2026-01-01")
			.session(manager)).andExpect(status().isBadRequest());
	}

}
