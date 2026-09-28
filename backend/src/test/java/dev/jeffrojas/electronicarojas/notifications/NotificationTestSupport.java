package dev.jeffrojas.electronicarojas.notifications;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;

/**
 * Fixture of the notification tests: staff of San José and Alajuela, and repair orders of a
 * fictitious customer with or without e-mail consent. The development inbox is the transport, and
 * the worker runs only when a test calls {@link NotificationDispatcher#runOnce()}.
 */
abstract class NotificationTestSupport extends IntegrationTestSupport {

	static final String CONSENT = """
			"consent":{"channels":["EMAIL"],"source":"IN_PERSON","textVersion":"AVISOS-2026-09"}""";

	@Autowired
	protected NotificationDispatcher dispatcher;

	@Autowired
	protected MailTransport transport;

	protected long sanJose;

	protected long alajuela;

	protected long technicianId;

	protected MockHttpSession manager;

	protected MockHttpSession receptionist;

	protected MockHttpSession technician;

	protected MockHttpSession alajuelaManager;

	@BeforeEach
	void setUpNotifications() throws Exception {
		inbox().clear();
		sanJose = createBranch("SJ-01");
		alajuela = createBranch("AL-01");
		createUser("gerente@electronica-rojas.test", Role.BRANCH_MANAGER, sanJose);
		createUser("recepcion@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		technicianId = createUser("tecnico@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		createUser("gerente.al@electronica-rojas.test", Role.BRANCH_MANAGER, alajuela);
		manager = login("gerente@electronica-rojas.test");
		receptionist = login("recepcion@electronica-rojas.test");
		technician = login("tecnico@electronica-rojas.test");
		alajuelaManager = login("gerente.al@electronica-rojas.test");
	}

	protected InboxMailTransport inbox() {
		return (InboxMailTransport) transport;
	}

	/**
	 * Receives an order for a new customer with an e-mail and, if {@code consent}, the e-mail consent
	 * accepted at the counter; then takes it to IN_REPAIR.
	 */
	protected long orderInRepair(boolean consent) throws Exception {
		String body = mockMvc.perform(post("/api/v1/repair-orders").session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"operationId":"%s","branchId":%d,
					 "newCustomer":{"fullName":"Lucía Ficticia","phone":"8800-0001","email":"lucia@ejemplo.test",
					                "allowDuplicatePhone":true%s},
					 "deviceType":"Lavadora","brand":"Whirlpool","reportedFault":"No centrifuga","physicalCondition":"Bueno"}
					""".formatted(UUID.randomUUID(), sanJose, consent ? "," + CONSENT : "")))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long orderId = ((Number) JsonPath.read(body, "$.id")).longValue();
		mockMvc.perform(put("/api/v1/repair-orders/{id}/technician", orderId).session(manager)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"technicianId\":%d}".formatted(technicianId))).andExpect(status().isOk());
		transition(orderId, "DIAGNOSING").andExpect(status().isOk());
		transition(orderId, "IN_REPAIR").andExpect(status().isOk());
		return orderId;
	}

	protected ResultActions transition(long orderId, String toStatus) throws Exception {
		return mockMvc.perform(post("/api/v1/repair-orders/{id}/status", orderId).session(technician)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"toStatus\":\"%s\"}".formatted(toStatus)));
	}

	protected long customerOf(long orderId) {
		return jdbc.queryForObject("SELECT customer_id FROM repair_orders WHERE id = ?", Long.class, orderId);
	}

	protected String outboxStatus(long orderId) {
		return jdbc.queryForObject(
				"SELECT status || coalesce(':' || skip_reason, '') FROM notification_outbox WHERE subject_type = 'REPAIR_ORDER' AND subject_id = ?",
				String.class, orderId);
	}

	protected ResultActions withdrawEmail(long customerId) throws Exception {
		return mockMvc.perform(post("/api/v1/customers/{id}/consents", customerId).session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"channel\":\"EMAIL\",\"granted\":false,\"source\":\"PHONE\"}"));
	}

}
