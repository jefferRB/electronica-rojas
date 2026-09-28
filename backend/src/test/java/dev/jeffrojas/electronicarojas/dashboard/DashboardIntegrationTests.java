package dev.jeffrojas.electronicarojas.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;
import dev.jeffrojas.electronicarojas.shared.ClockConfig;

/**
 * D.1/D.2: every indicator equals the total of the list it links to, for the same branch scope, and
 * never counts branches the user cannot see. Technicians get their own work only.
 */
class DashboardIntegrationTests extends IntegrationTestSupport {

	private long sanJose;

	private long alajuela;

	private long technicianId;

	private MockHttpSession manager;

	private MockHttpSession receptionist;

	private MockHttpSession technician;

	private MockHttpSession alajuelaReceptionist;

	@BeforeEach
	void setUp() throws Exception {
		sanJose = createBranch("SJ-01");
		alajuela = createBranch("AL-01");
		createUser("gerente@electronica-rojas.test", Role.BRANCH_MANAGER, sanJose);
		createUser("recepcion@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		technicianId = createUser("tecnico@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		createUser("recepcion.al@electronica-rojas.test", Role.RECEPTIONIST, alajuela);
		manager = login("gerente@electronica-rojas.test");
		receptionist = login("recepcion@electronica-rojas.test");
		technician = login("tecnico@electronica-rojas.test");
		alajuelaReceptionist = login("recepcion.al@electronica-rojas.test");
	}

	private long receive(MockHttpSession session, long branchId) throws Exception {
		String body = mockMvc.perform(post("/api/v1/repair-orders").session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"operationId":"%s","branchId":%d,
					 "newCustomer":{"fullName":"Cliente Ficticio","phone":"8888-7777","allowDuplicatePhone":true},
					 "deviceType":"Lavadora","brand":"Whirlpool","reportedFault":"No centrifuga","physicalCondition":"Bueno"}
					""".formatted(UUID.randomUUID(), branchId)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private void diagnosing(long orderId) throws Exception {
		mockMvc.perform(put("/api/v1/repair-orders/{id}/technician", orderId).session(manager)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"technicianId\":%d}".formatted(technicianId))).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/repair-orders/{id}/status", orderId).session(technician)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"toStatus\":\"DIAGNOSING\"}")).andExpect(status().isOk());
	}

	private Map<String, Integer> indicators(MockHttpSession session, Long branchId) throws Exception {
		var request = get("/api/v1/dashboard").session(session);
		if (branchId != null) {
			request = request.param("branchId", String.valueOf(branchId));
		}
		String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> list = JsonPath.read(body, "$.indicators");
		return list.stream()
			.collect(java.util.stream.Collectors.toMap(item -> (String) item.get("key"),
					item -> ((Number) item.get("count")).intValue()));
	}

	private int listTotal(MockHttpSession session, String path, Map<String, String> params) throws Exception {
		var request = get(path).session(session);
		for (var entry : params.entrySet()) {
			request = request.param(entry.getKey(), entry.getValue());
		}
		String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.totalElements")).intValue();
	}

	@Test
	void repairAndRequestIndicatorsEqualTheirListsForTheBranch() throws Exception {
		receive(receptionist, sanJose);
		receive(receptionist, sanJose);
		diagnosing(receive(receptionist, sanJose));
		receive(alajuelaReceptionist, alajuela);
		jdbc.update("""
				INSERT INTO service_requests (request_code, public_ref, submission_id, submission_fingerprint, channel, status,
				    branch_id, contact_name, contact_phone, province, canton, address_line, device_type, problem_description,
				    preferred_window, contact_consent_at, notifications_consent, created_at, updated_at)
				VALUES ('SR-2026-000900', gen_random_uuid(), gen_random_uuid(), 'x', 'PUBLIC_FORM', 'PENDING', ?,
				    'Cliente Ficticio', '+50688887777', 'SAN_JOSE', 'Escazú', 'Calle 1', 'Refrigeradora', 'No enfría bien',
				    'ANY', now(), FALSE, now(), now())""", sanJose);

		Map<String, Integer> branch = indicators(manager, sanJose);

		assertThat(branch.get("REPAIRS_RECEIVED")).isEqualTo(2)
			.isEqualTo(listTotal(manager, "/api/v1/repair-orders", Map.of("branchId", "" + sanJose, "status", "RECEIVED")));
		assertThat(branch.get("REPAIRS_DIAGNOSING")).isEqualTo(1)
			.isEqualTo(listTotal(manager, "/api/v1/repair-orders", Map.of("branchId", "" + sanJose, "status", "DIAGNOSING")));
		assertThat(branch.get("REPAIRS_READY")).isZero();
		assertThat(branch.get("REQUESTS_PENDING")).isEqualTo(1)
			.isEqualTo(listTotal(manager, "/api/v1/service-requests", Map.of("branchId", "" + sanJose, "status", "PENDING")));
		assertThat(branch).doesNotContainKey("MY_REPAIRS_IN_REPAIR");
	}

	@Test
	void theConsolidatedViewCoversOnlyTheUsersBranches() throws Exception {
		receive(receptionist, sanJose);
		receive(alajuelaReceptionist, alajuela);
		receive(alajuelaReceptionist, alajuela);

		// A manager of San José only: "all my branches" is San José.
		assertThat(indicators(manager, null).get("REPAIRS_RECEIVED")).isEqualTo(1);
		// The administrator sees every branch.
		MockHttpSession admin = loginAsAdmin();
		assertThat(indicators(admin, null).get("REPAIRS_RECEIVED")).isEqualTo(3)
			.isEqualTo(listTotal(admin, "/api/v1/repair-orders", Map.of("status", "RECEIVED")));
		mockMvc.perform(get("/api/v1/dashboard").param("branchId", String.valueOf(alajuela)).session(manager))
			.andExpect(status().isNotFound());
		// A receptionist works on one branch at a time.
		mockMvc.perform(get("/api/v1/dashboard").session(receptionist))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("BRANCH_REQUIRED"));
		mockMvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
	}

	@Test
	void stockIndicatorsUseTheSameRuleAsTheStockScreens() throws Exception {
		long empty = createProduct("SIN-01");
		long low = createProduct("BAJO-01");
		long fine = createProduct("BIEN-01");
		long inactive = createProduct("VIEJO-01", false);
		setStock(sanJose, empty, 0);
		setStock(sanJose, low, 1);
		jdbc.update("UPDATE branch_stock SET minimum_quantity = 3 WHERE product_id = ? AND branch_id = ?", low, sanJose);
		setStock(sanJose, fine, 10);
		setStock(sanJose, inactive, 0);
		setStock(alajuela, fine, 0);

		Map<String, Integer> branch = indicators(receptionist, sanJose);
		assertThat(branch.get("STOCK_OUT")).isEqualTo(1).isEqualTo(listTotal(receptionist,
				"/api/v1/branches/" + sanJose + "/stock", Map.of("stockStatus", "OUT_OF_STOCK")));
		assertThat(branch.get("STOCK_LOW")).isEqualTo(1)
			.isEqualTo(listTotal(receptionist, "/api/v1/branches/" + sanJose + "/stock", Map.of("stockStatus", "LOW")));

		// Consolidated for the administrator: a product counts once if it is out anywhere.
		MockHttpSession admin = loginAsAdmin();
		String overview = mockMvc.perform(get("/api/v1/stock/overview").param("stockStatus", "OUT_OF_STOCK").session(admin))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(indicators(admin, null).get("STOCK_OUT")).isEqualTo(3)
			.isEqualTo(((Number) JsonPath.read(overview, "$.rows.totalElements")).intValue());
	}

	@Test
	void techniciansSeeTheirOwnWorkNotAdministrativeFigures() throws Exception {
		long mine = receive(receptionist, sanJose);
		diagnosing(mine);
		receive(receptionist, sanJose);

		Map<String, Integer> own = indicators(technician, null);

		assertThat(own).containsEntry("MY_REPAIRS_DIAGNOSING", 1)
			.containsEntry("MY_REPAIRS_RECEIVED", 0)
			.containsKey("MY_VISITS_TODAY")
			.doesNotContainKeys("STOCK_OUT", "REQUESTS_PENDING", "REPAIRS_RECEIVED");
		assertThat(own.get("MY_REPAIRS_DIAGNOSING"))
			.isEqualTo(listTotal(technician, "/api/v1/repair-orders", Map.of("status", "DIAGNOSING")));
	}

	@Test
	void visitsOfTodayAndTheNextDaysComeFromTheAgenda() throws Exception {
		long customer = jdbc.queryForObject("""
				INSERT INTO customers (full_name, search_name, phone, registered_branch_id, created_by, created_at, updated_at)
				VALUES ('Cliente Ficticio', 'cliente ficticio', '+50688887777', ?, ?, now(), now()) RETURNING id""",
				Long.class, sanJose, technicianId);
		LocalDate today = LocalDate.now(ClockConfig.BUSINESS_ZONE);
		Instant earlyToday = today.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant().plus(1, ChronoUnit.HOURS);
		Instant inTwoDays = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
		insertVisit(customer, 1, earlyToday, "CONFIRMED");
		insertVisit(customer, 2, inTwoDays, "CONFIRMED");
		insertVisit(customer, 3, inTwoDays.plus(1, ChronoUnit.DAYS), "CANCELLED");

		String body = mockMvc.perform(get("/api/v1/dashboard").param("branchId", String.valueOf(sanJose)).session(manager))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Instant dayStart = today.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
		String agenda = mockMvc.perform(get("/api/v1/service-visits").param("from", dayStart.toString())
			.param("to", dayStart.plus(1, ChronoUnit.DAYS).toString())
			.param("branchId", String.valueOf(sanJose))
			.session(manager)).andReturn().getResponse().getContentAsString();

		List<Map<String, Object>> list = JsonPath.read(body, "$.indicators[?(@.key == 'VISITS_TODAY')]");
		assertThat(((Number) list.get(0).get("count")).intValue()).isEqualTo(((List<?>) JsonPath.read(agenda, "$")).size())
			.isGreaterThanOrEqualTo(1);
		List<String> upcoming = JsonPath.read(body, "$.upcomingVisits[*].requestCode");
		assertThat(upcoming).contains("SR-2026-000802").doesNotContain("SR-2026-000803");
		// Another branch's staff sees none of them.
		assertThat(JsonPath.<List<?>>read(mockMvc.perform(get("/api/v1/dashboard").param("branchId", String.valueOf(alajuela))
			.session(alajuelaReceptionist)).andReturn().getResponse().getContentAsString(), "$.upcomingVisits")).isEmpty();
	}

	private void insertVisit(long customerId, int number, Instant start, String status) {
		long requestId = jdbc.queryForObject("""
				INSERT INTO service_requests (request_code, public_ref, submission_id, submission_fingerprint, channel, status,
				    branch_id, customer_id, contact_name, contact_phone, province, canton, address_line, device_type,
				    problem_description, preferred_window, contact_consent_at, notifications_consent, created_at, updated_at)
				VALUES (?, gen_random_uuid(), gen_random_uuid(), 'x', 'PUBLIC_FORM', 'ACCEPTED', ?, ?, 'Cliente Ficticio',
				    '+50688887777', 'SAN_JOSE', 'Escazú', 'Calle 1', 'Refrigeradora', 'No enfría bien', 'ANY', now(), FALSE,
				    now(), now()) RETURNING id""", Long.class, "SR-2026-00080" + number, sanJose, customerId);
		jdbc.update("""
				INSERT INTO service_visits (request_id, operation_id, technician_id, status, scheduled_start, scheduled_end,
				    blocked_until, cancel_reason, created_by, created_at, updated_at)
				VALUES (?, gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, now(), now())""", requestId, technicianId, status,
				java.sql.Timestamp.from(start), java.sql.Timestamp.from(start.plus(90, ChronoUnit.MINUTES)),
				java.sql.Timestamp.from(start.plus(120, ChronoUnit.MINUTES)), "CANCELLED".equals(status) ? "Prueba" : null,
				technicianId);
	}

}
