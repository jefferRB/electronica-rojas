package dev.jeffrojas.electronicarojas.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;

/**
 * BR-CUS-006 (C.1): consent per channel is an explicit, dated, sourced and versioned statement;
 * contact data never implies it, it can be withdrawn through an authorized flow and its history is
 * never rewritten.
 */
class CustomerConsentIntegrationTests extends IntegrationTestSupport {

	private static final String TEXT = "AVISOS-2026-09";

	private long sanJose;

	private MockHttpSession receptionist;

	private MockHttpSession alajuelaReceptionist;

	private MockHttpSession technician;

	@BeforeEach
	void setUp() throws Exception {
		sanJose = createBranch("SJ-01");
		long alajuela = createBranch("AL-01");
		createUser("recepcion@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		createUser("recepcion.al@electronica-rojas.test", Role.RECEPTIONIST, alajuela);
		createUser("tecnico@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		receptionist = login("recepcion@electronica-rojas.test");
		alajuelaReceptionist = login("recepcion.al@electronica-rojas.test");
		technician = login("tecnico@electronica-rojas.test");
	}

	private long createCustomer(String email, String consent) throws Exception {
		String body = mockMvc.perform(post("/api/v1/customers").session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"branchId":%d,"customer":{"fullName":"Lucía Ficticia","phone":"8800-0001",%s
					 "allowDuplicatePhone":true%s}}
					""".formatted(sanJose, email == null ? "" : "\"email\":\"" + email + "\",",
					consent == null ? "" : ",\"consent\":" + consent)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions record(MockHttpSession session, long customerId, String json) throws Exception {
		return mockMvc.perform(post("/api/v1/customers/{id}/consents", customerId).session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	@Test
	void givingAnEmailOrPhoneIsNeverConsent() throws Exception {
		long customerId = createCustomer("lucia@ejemplo.test", null);

		assertThat(countRows("customer_consents")).isZero();
		mockMvc.perform(get("/api/v1/customers/{id}/consents", customerId).session(receptionist))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasEmail").value(true))
			.andExpect(jsonPath("$.currentTextVersion").value(TEXT))
			.andExpect(jsonPath("$.channels[0].channel").value("EMAIL"))
			.andExpect(jsonPath("$.channels[0].latest").value(nullValue()))
			.andExpect(jsonPath("$.channels[1].channel").value("WHATSAPP"))
			.andExpect(jsonPath("$.channels[1].latest").value(nullValue()));
	}

	@Test
	void consentAcceptedAtRegistrationIsRecordedPerChannelWithSourceAndVersion() throws Exception {
		long customerId = createCustomer("lucia@ejemplo.test",
				"{\"channels\":[\"EMAIL\",\"WHATSAPP\"],\"source\":\"IN_PERSON\",\"textVersion\":\"" + TEXT + "\"}");

		assertThat(jdbc.queryForList(
				"SELECT channel || ':' || granted || ':' || source || ':' || text_version FROM customer_consents WHERE customer_id = ? ORDER BY channel",
				String.class, customerId)).containsExactly("EMAIL:true:IN_PERSON:" + TEXT, "WHATSAPP:true:IN_PERSON:" + TEXT);
		// The audit keeps codes, never the address.
		assertThat(jdbc.queryForList("SELECT summary || details::text FROM audit_events WHERE action = 'CUSTOMER_CONSENT_RECORDED'",
				String.class)).hasSize(2).allSatisfy(text -> assertThat(text).doesNotContain("lucia", "8800"));
	}

	@Test
	void consentCanBeWithdrawnAndGrantedAgainKeepingTheWholeHistory() throws Exception {
		long customerId = createCustomer("lucia@ejemplo.test", null);

		record(receptionist, customerId, "{\"channel\":\"EMAIL\",\"granted\":true,\"source\":\"PHONE\",\"textVersion\":\"" + TEXT + "\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.channels[0].latest.granted").value(true))
			.andExpect(jsonPath("$.channels[0].latest.source").value("PHONE"))
			.andExpect(jsonPath("$.channels[0].latest.recordedBy.fullName").value("Usuario RECEPTIONIST"));
		record(receptionist, customerId, "{\"channel\":\"EMAIL\",\"granted\":false,\"source\":\"WRITTEN\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.channels[0].latest.granted").value(false))
			.andExpect(jsonPath("$.history.length()").value(2));

		assertThatThrownBy(() -> jdbc.update("UPDATE customer_consents SET granted = TRUE")).hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.update("DELETE FROM customer_consents")).hasMessageContaining("append-only");
	}

	@Test
	void invalidStatementsAreRejected() throws Exception {
		long withoutEmail = createCustomer(null, null);
		record(receptionist, withoutEmail, "{\"channel\":\"EMAIL\",\"granted\":true,\"source\":\"IN_PERSON\",\"textVersion\":\"" + TEXT + "\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_REQUIRED"));
		// WhatsApp needs only the phone the customer always has.
		record(receptionist, withoutEmail, "{\"channel\":\"WHATSAPP\",\"granted\":true,\"source\":\"IN_PERSON\",\"textVersion\":\"" + TEXT + "\"}")
			.andExpect(status().isOk());
		record(receptionist, withoutEmail, "{\"channel\":\"WHATSAPP\",\"granted\":true,\"source\":\"IN_PERSON\",\"textVersion\":\"AVISOS-2020-01\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("CONSENT_TEXT_OUTDATED"));
		record(receptionist, withoutEmail, "{\"channel\":\"WHATSAPP\",\"granted\":true,\"source\":\"PUBLIC_FORM\",\"textVersion\":\"" + TEXT + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("CONSENT_SOURCE_INVALID"));
		assertThat(countRows("customer_consents")).isEqualTo(1);
	}

	@Test
	void onlyStaffWithAccessToTheCustomerManagesConsent() throws Exception {
		long customerId = createCustomer("lucia@ejemplo.test", null);
		String grant = "{\"channel\":\"EMAIL\",\"granted\":true,\"source\":\"IN_PERSON\",\"textVersion\":\"" + TEXT + "\"}";

		record(technician, customerId, grant).andExpect(status().isForbidden());
		record(alajuelaReceptionist, customerId, grant).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/customers/{id}/consents", customerId).session(alajuelaReceptionist))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/customers/{id}/consents", customerId).session(receptionist)
			.contentType(MediaType.APPLICATION_JSON)
			.content(grant)).andExpect(status().isForbidden());
		assertThat(countRows("customer_consents")).isZero();
	}

	@Test
	void receptionCanRecordConsentOfTheNewCustomer() throws Exception {
		mockMvc.perform(post("/api/v1/repair-orders").session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"operationId":"%s","branchId":%d,
					 "newCustomer":{"fullName":"Lucía Ficticia","phone":"8800-0001","email":"lucia@ejemplo.test",
					   "consent":{"channels":["EMAIL"],"source":"IN_PERSON","textVersion":"%s"}},
					 "deviceType":"Lavadora","brand":"Whirlpool","reportedFault":"No centrifuga","physicalCondition":"Bueno"}
					""".formatted(UUID.randomUUID(), sanJose, TEXT))).andExpect(status().isCreated());

		assertThat(jdbc.queryForList("SELECT channel FROM customer_consents WHERE granted", String.class))
			.containsExactly("EMAIL");
	}

}
