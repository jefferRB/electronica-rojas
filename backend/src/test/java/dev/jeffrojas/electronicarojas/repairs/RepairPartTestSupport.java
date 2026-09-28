package dev.jeffrojas.electronicarojas.repairs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;

/**
 * Shared fixture of the spare-part tests: two branches, staff of San José (manager, receptionist,
 * two technicians), a manager of Alajuela, and helpers to bring an order to IN_REPAIR and to call
 * the part endpoints. All data is fictitious.
 */
abstract class RepairPartTestSupport extends IntegrationTestSupport {

	protected long sanJose;

	protected long alajuela;

	protected long technicianId;

	protected long otherTechnicianId;

	protected MockHttpSession manager;

	protected MockHttpSession receptionist;

	protected MockHttpSession technician;

	protected MockHttpSession otherTechnician;

	protected MockHttpSession alajuelaManager;

	@BeforeEach
	void setUpStaff() throws Exception {
		sanJose = createBranch("SJ-01");
		alajuela = createBranch("AL-01");
		createUser("gerente@electronica-rojas.test", Role.BRANCH_MANAGER, sanJose);
		createUser("recepcion@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		technicianId = createUser("tecnico@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		otherTechnicianId = createUser("tecnico2@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		createUser("gerente.al@electronica-rojas.test", Role.BRANCH_MANAGER, alajuela);
		manager = login("gerente@electronica-rojas.test");
		receptionist = login("recepcion@electronica-rojas.test");
		technician = login("tecnico@electronica-rojas.test");
		otherTechnician = login("tecnico2@electronica-rojas.test");
		alajuelaManager = login("gerente.al@electronica-rojas.test");
	}

	/** Receives an order at San José, assigns the technician and moves it to IN_REPAIR. */
	protected long orderInRepair() throws Exception {
		String body = mockMvc.perform(post("/api/v1/repair-orders").session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"operationId":"%s","branchId":%d,
					 "newCustomer":{"fullName":"Cliente Ficticio","phone":"8888-7777","allowDuplicatePhone":true},
					 "deviceType":"Lavadora","brand":"Whirlpool","reportedFault":"No centrifuga","physicalCondition":"Bueno"}
					""".formatted(UUID.randomUUID(), sanJose)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long orderId = ((Number) JsonPath.read(body, "$.id")).longValue();
		mockMvc.perform(put("/api/v1/repair-orders/{id}/technician", orderId).session(manager)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"technicianId\":%d}".formatted(technicianId))).andExpect(status().isOk());
		transition(technician, orderId, "DIAGNOSING").andExpect(status().isOk());
		transition(technician, orderId, "IN_REPAIR").andExpect(status().isOk());
		return orderId;
	}

	protected ResultActions transition(MockHttpSession session, long orderId, String toStatus) throws Exception {
		return mockMvc.perform(post("/api/v1/repair-orders/{id}/status", orderId).session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"toStatus\":\"%s\"}".formatted(toStatus)));
	}

	protected static String consumeJson(UUID operationId, long productId, int quantity) {
		return "{\"operationId\":\"%s\",\"productId\":%d,\"quantity\":%d}".formatted(operationId, productId, quantity);
	}

	protected static String returnJson(UUID operationId, int quantity, String reason) {
		return "{\"operationId\":\"%s\",\"quantity\":%d,\"reason\":\"%s\"}".formatted(operationId, quantity, reason);
	}

	protected ResultActions consume(MockHttpSession session, long orderId, UUID operationId, long productId,
			int quantity) throws Exception {
		return mockMvc.perform(post("/api/v1/repair-orders/{id}/parts", orderId).session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(consumeJson(operationId, productId, quantity)));
	}

	protected ResultActions returnPart(MockHttpSession session, long orderId, long usageId, UUID operationId,
			int quantity, String reason) throws Exception {
		return mockMvc.perform(post("/api/v1/repair-orders/{id}/parts/{usage}/returns", orderId, usageId).session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(returnJson(operationId, quantity, reason)));
	}

	protected long usageIdOf(long orderId, long productId) {
		return jdbc.queryForObject("SELECT max(id) FROM repair_part_usages WHERE order_id = ? AND product_id = ?",
				Long.class, orderId, productId);
	}

	protected int movementsOfOrder(long orderId) {
		return jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE repair_order_id = ?", Integer.class,
				orderId);
	}

}
