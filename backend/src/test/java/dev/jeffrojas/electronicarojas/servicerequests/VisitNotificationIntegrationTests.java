package dev.jeffrojas.electronicarojas.servicerequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import dev.jeffrojas.electronicarojas.security.Role;

/**
 * C.1/C.2 for home service: confirmed, rescheduled and cancelled visits notify through the outbox
 * (only for visits the customer was promised), and the public form's channel opt-ins become the
 * customer's consent only when staff links the request and the contact data matches.
 */
class VisitNotificationIntegrationTests extends HomeServiceTestSupport {

	private static final String TEXT = "AVISOS-2026-09";

	private long sanJose;

	private long technicianId;

	private MockHttpSession receptionist;

	private LocalDate monday;

	@BeforeEach
	void setUp() throws Exception {
		sanJose = createBranch("SJ-01");
		createUser("recepcion@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		technicianId = createUser("tecnico@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		weekdayShifts(technicianId, sanJose);
		receptionist = login("recepcion@electronica-rojas.test");
		monday = nextMonday();
	}

	/** Staff request of a new customer who has an e-mail and accepted e-mail notices by phone. */
	private long requestOfConsentingCustomer() throws Exception {
		long requestId = staffRequest(receptionist, sanJose, "Lucía Ficticia", "8800-0001");
		long customerId = jdbc.queryForObject("SELECT customer_id FROM service_requests WHERE id = ?", Long.class, requestId);
		jdbc.update("UPDATE customers SET email = 'lucia@ejemplo.test' WHERE id = ?", customerId);
		mockMvc.perform(post("/api/v1/customers/{id}/consents", customerId).session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"channel\":\"EMAIL\",\"granted\":true,\"source\":\"PHONE\",\"textVersion\":\"" + TEXT + "\"}"))
			.andExpect(status().isOk());
		return requestId;
	}

	private String events() {
		return String.join(",", jdbc.queryForList(
				"SELECT event_type || ':' || status FROM notification_outbox ORDER BY id", String.class));
	}

	@Test
	void confirmedRescheduledAndCancelledVisitsNotifyTheCustomer() throws Exception {
		long requestId = requestOfConsentingCustomer();
		long visitId = idOf(schedule(receptionist, requestId, technicianId, at(monday, "09:00"), true)
			.andExpect(status().isCreated()));
		assertThat(events()).isEqualTo("VISIT_CONFIRMED:PENDING");

		mockMvc.perform(put("/api/v1/service-visits/{id}/schedule", visitId).session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"technicianId\":%d,\"start\":\"%s\",\"durationMinutes\":90,\"reason\":\"Pedido del cliente\"}"
				.formatted(technicianId, at(monday, "14:00")))).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/service-visits/{id}/cancel", visitId).session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"El cliente viaja\"}")).andExpect(status().isOk());

		assertThat(events()).isEqualTo("VISIT_CONFIRMED:PENDING,VISIT_RESCHEDULED:PENDING,VISIT_CANCELLED:PENDING");
		String payload = jdbc.queryForObject(
				"SELECT payload::text FROM notification_outbox WHERE event_type = 'VISIT_RESCHEDULED'", String.class);
		assertThat(payload).contains("previousStart", at(monday, "09:00"), at(monday, "14:00"))
			// Staff reasons and the address stay internal.
			.doesNotContain("Pedido del cliente", "Barrio");
		mockMvc.perform(get("/api/v1/service-requests/{id}", requestId).session(receptionist))
			.andExpect(jsonPath("$.notifications.length()").value(3))
			.andExpect(jsonPath("$.notifications[2].eventType").value("VISIT_CANCELLED"));
	}

	@Test
	void aProposedVisitIsNotNewsForTheCustomer() throws Exception {
		long requestId = requestOfConsentingCustomer();
		long visitId = idOf(schedule(receptionist, requestId, technicianId, at(monday, "09:00"), false)
			.andExpect(status().isCreated()));
		mockMvc.perform(put("/api/v1/service-visits/{id}/schedule", visitId).session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"technicianId\":%d,\"start\":\"%s\",\"durationMinutes\":90}".formatted(technicianId,
					at(monday, "10:30")))).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/service-visits/{id}/cancel", visitId).session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Sin respuesta\"}")).andExpect(status().isOk());

		assertThat(countRows("notification_outbox")).isZero();
	}

	@Test
	void cancellingTheRequestNotifiesTheConfirmedVisitCancellation() throws Exception {
		long requestId = requestOfConsentingCustomer();
		schedule(receptionist, requestId, technicianId, at(monday, "09:00"), true).andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/service-requests/{id}/cancel", requestId).session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Ya no lo necesita\"}")).andExpect(status().isOk());

		assertThat(events()).isEqualTo("VISIT_CONFIRMED:PENDING,VISIT_CANCELLED:PENDING");
	}

	// ---- public form consents ----

	private long submitPublic(String email, boolean emailNotifications, boolean whatsappNotifications, String version)
			throws Exception {
		String body = mockMvc.perform(post("/api/v1/public/service-requests").with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"submissionId":"%s","branchId":%d,"contactName":"Lucía Ficticia","contactPhone":"8800-0001",%s
					 "province":"SAN_JOSE","canton":"Montes de Oca","addressLine":"Del parque 200 m al este",
					 "deviceType":"Refrigeradora","problemDescription":"No enfría la parte de abajo","contactConsent":true,
					 "emailNotifications":%s,"whatsappNotifications":%s,"consentTextVersion":%s}
					""".formatted(UUID.randomUUID(), sanJose, email == null ? "" : "\"contactEmail\":\"" + email + "\",",
					emailNotifications, whatsappNotifications, version == null ? "null" : "\"" + version + "\"")))
			.andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String code = JsonPath.read(body, "$.requestCode");
		return jdbc.queryForObject("SELECT id FROM service_requests WHERE request_code = ?", Long.class, code);
	}

	private void linkAsNewCustomer(long requestId) throws Exception {
		mockMvc.perform(put("/api/v1/service-requests/{id}/customer", requestId).session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"registerNew\":true,\"confirmedNewPerson\":true}")).andExpect(status().isOk());
	}

	@Test
	void publicFormOptInsBecomeConsentWhenStaffLinksTheCustomer() throws Exception {
		long requestId = submitPublic("lucia@ejemplo.test", true, true, TEXT);
		assertThat(countRows("customer_consents")).isZero();
		mockMvc.perform(get("/api/v1/service-requests/{id}", requestId).session(receptionist))
			.andExpect(jsonPath("$.emailConsent").value(true))
			.andExpect(jsonPath("$.whatsappConsent").value(true));

		linkAsNewCustomer(requestId);

		String code = jdbc.queryForObject("SELECT request_code FROM service_requests WHERE id = ?", String.class, requestId);
		assertThat(jdbc.queryForList("""
				SELECT c.channel || ':' || c.source || ':' || c.reference || ':' || (c.stated_at = r.created_at)
				FROM customer_consents c JOIN service_requests r ON r.customer_id = c.customer_id
				WHERE r.id = ? ORDER BY c.channel""", String.class, requestId))
			.containsExactly("EMAIL:PUBLIC_FORM:" + code + ":true", "WHATSAPP:PUBLIC_FORM:" + code + ":true");
	}

	@Test
	void anEmailConsentForAnotherAddressIsNotApplied() throws Exception {
		long requestId = submitPublic("lucia@ejemplo.test", true, false, TEXT);
		long existing = jdbc.queryForObject("""
				INSERT INTO customers (full_name, search_name, phone, email, registered_branch_id, created_by, created_at, updated_at)
				VALUES ('Lucía Ficticia', 'lucia ficticia', '+50688000001', 'otra@ejemplo.test', ?,
				        (SELECT id FROM app_users WHERE email = 'recepcion@electronica-rojas.test'), now(), now()) RETURNING id""",
				Long.class, sanJose);

		mockMvc.perform(put("/api/v1/service-requests/{id}/customer", requestId).session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"customerId\":%d}".formatted(existing))).andExpect(status().isOk());

		assertThat(countRows("customer_consents")).isZero();
	}

	@Test
	void theFormValidatesChannelConsentAndNeverReinterpretsTheOldOptIn() throws Exception {
		mockMvc.perform(post("/api/v1/public/service-requests").with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"submissionId":"%s","branchId":%d,"contactName":"Lucía Ficticia","contactPhone":"8800-0001",
					 "province":"SAN_JOSE","canton":"Montes de Oca","addressLine":"Del parque 200 m al este",
					 "deviceType":"Refrigeradora","problemDescription":"No enfría la parte de abajo","contactConsent":true,
					 "emailNotifications":true,"consentTextVersion":"%s"}
					""".formatted(UUID.randomUUID(), sanJose, TEXT)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("EMAIL_REQUIRED_FOR_CONSENT"));
		mockMvc.perform(post("/api/v1/public/service-requests").with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"submissionId":"%s","branchId":%d,"contactName":"Lucía Ficticia","contactPhone":"8800-0001",
					 "province":"SAN_JOSE","canton":"Montes de Oca","addressLine":"Del parque 200 m al este",
					 "deviceType":"Refrigeradora","problemDescription":"No enfría la parte de abajo","contactConsent":true,
					 "whatsappNotifications":true,"consentTextVersion":"AVISOS-2020-01"}
					""".formatted(UUID.randomUUID(), sanJose)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("CONSENT_TEXT_OUTDATED"));

		// A Phase 4 style request (channel-less opt-in) links without creating any consent.
		String body = mockMvc.perform(post("/api/v1/public/service-requests").with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(publicForm(UUID.randomUUID(), sanJose, "Lucía Ficticia", "8800-0001")))
			.andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long legacy = jdbc.queryForObject("SELECT id FROM service_requests WHERE request_code = ?", Long.class,
				(String) JsonPath.read(body, "$.requestCode"));
		linkAsNewCustomer(legacy);
		assertThat(countRows("customer_consents")).isZero();
	}

}
