package dev.jeffrojas.electronicarojas.servicerequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import dev.jeffrojas.electronicarojas.security.Role;
import com.jayway.jsonpath.JsonPath;

/**
 * FR-SRV-001/002, BR-SRV-001/004: the anonymous form, its validation, idempotency, abuse limits,
 * CSRF and the absence of any way to discover customers.
 */
class PublicServiceRequestIntegrationTests extends HomeServiceTestSupport {

	private long sanJose;

	@BeforeEach
	void setUp() {
		sanJose = createBranch("SJ-01");
		createBranch("OLD-01", false);
	}

	private static RequestPostProcessor from(String address) {
		return request -> {
			request.setRemoteAddr(address);
			return request;
		};
	}

	private ResultActions submit(String json, String address) throws Exception {
		return mockMvc.perform(post("/api/v1/public/service-requests").with(xsrf())
			.with(from(address))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private ResultActions submit(String json) throws Exception {
		return submit(json, "203.0.113.10");
	}

	@Test
	void anonymousUsersSeeOnlyActiveBranchNames() throws Exception {
		mockMvc.perform(get("/api/v1/public/branches"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].name").value("Sucursal SJ-01"))
			.andExpect(jsonPath("$[0].code").doesNotExist());
	}

	@Test
	void validSubmissionIsReceivedPendingNotScheduled() throws Exception {
		String response = submit(publicForm(UUID.randomUUID(), sanJose, "  Ana   Pérez ", "8888-7777"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.requestCode").value(org.hamcrest.Matchers.matchesPattern("SR-\\d{4}-\\d{6}")))
			.andExpect(jsonPath("$.status").value("RECEIVED"))
			.andReturn().getResponse().getContentAsString();

		assertThat(jdbc.queryForMap("SELECT status, contact_name, contact_phone, customer_id, channel FROM service_requests"))
			.containsEntry("status", "PENDING")
			.containsEntry("contact_name", "Ana Pérez")
			.containsEntry("contact_phone", "+50688887777")
			.containsEntry("customer_id", null)
			.containsEntry("channel", "PUBLIC_FORM");
		assertThat(countRows("service_visits")).isZero();
		assertThat(jdbc.queryForList("SELECT event_type FROM service_request_events", String.class)).containsExactly("SUBMITTED");
		// The audit has no personal data.
		assertThat(jdbc.queryForObject("SELECT summary || ' ' || details::text FROM audit_events", String.class))
			.contains("SR-")
			.doesNotContain("Ana", "8888", "ejemplo", "parque");

		String publicRef = JsonPath.read(response, "$.publicRef");
		mockMvc.perform(get("/api/v1/public/service-requests/{ref}", publicRef))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RECEIVED"))
			.andExpect(jsonPath("$.branchName").value("Sucursal SJ-01"))
			.andExpect(jsonPath("$.visitDate").doesNotExist())
			.andExpect(jsonPath("$.contactName").doesNotExist())
			.andExpect(jsonPath("$.contactPhone").doesNotExist())
			.andExpect(jsonPath("$.addressLine").doesNotExist());
	}

	@Test
	void invalidInputIsRejectedWithStableCodes() throws Exception {
		String valid = publicForm(UUID.randomUUID(), sanJose, "Ana", "8888-7777");
		submit(valid.replace("\"contactConsent\":true", "\"contactConsent\":false")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("contactConsent"));
		submit(valid.replace("8888-7777", "12345")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("PHONE_INVALID"));
		submit(valid.replace("No enfría la parte de abajo", "Mal")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("problemDescription"));
		submit(valid.replace("\"branchId\":" + sanJose, "\"branchId\":999999")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("BRANCH_INVALID"));
		submit(valid.replace("\"preferredWindow\"", "\"preferredDate\":\"2000-01-01\",\"preferredWindow\""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("DATE_IN_PAST"));
		submit(valid.replace("\"SAN_JOSE\"", "\"ATLANTIDA\"")).andExpect(status().isBadRequest());
		assertThat(countRows("service_requests")).isZero();
	}

	@Test
	void csrfIsStillRequired() throws Exception {
		mockMvc.perform(post("/api/v1/public/service-requests").contentType(MediaType.APPLICATION_JSON)
			.content(publicForm(UUID.randomUUID(), sanJose, "Ana", "8888-7777"))).andExpect(status().isForbidden());
		assertThat(countRows("service_requests")).isZero();
	}

	@Test
	void aDoubleSubmitCreatesOneRequestAndAReusedIdWithOtherDataIsRefused() throws Exception {
		UUID submission = UUID.randomUUID();
		String first = submit(publicForm(submission, sanJose, "Ana", "8888-7777")).andExpect(status().isAccepted())
			.andReturn().getResponse().getContentAsString();
		String again = submit(publicForm(submission, sanJose, "Ana", "8888-7777")).andExpect(status().isAccepted())
			.andReturn().getResponse().getContentAsString();

		assertThat((String) JsonPath.read(again, "$.publicRef")).isEqualTo(JsonPath.read(first, "$.publicRef"));
		assertThat(countRows("service_requests")).isEqualTo(1);
		submit(publicForm(submission, sanJose, "Otra persona", "8888-7777")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OPERATION_ID_REUSED"));
	}

	@Test
	void honeypotSubmissionsLookAcceptedButAreDiscarded() throws Exception {
		String bot = publicForm(UUID.randomUUID(), sanJose, "Bot", "8888-7777")
			.replace("\"contactConsent\":true", "\"contactConsent\":true,\"website\":\"http://spam.example\"");
		submit(bot).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("RECEIVED"));
		assertThat(countRows("service_requests")).isZero();
	}

	@Test
	void tooManySubmissionsFromOneAddressOrForOnePhoneAreLimited() throws Exception {
		for (int i = 0; i < 5; i++) {
			submit(publicForm(UUID.randomUUID(), sanJose, "Cliente " + i, "8000-000" + i), "198.51.100.7")
				.andExpect(status().isAccepted());
		}
		submit(publicForm(UUID.randomUUID(), sanJose, "Cliente 6", "8000-0006"), "198.51.100.7")
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.code").value("RATE_LIMITED"));

		for (int i = 0; i < 3; i++) {
			submit(publicForm(UUID.randomUUID(), sanJose, "Misma", "7000-1111"), "192.0.2." + i).andExpect(status().isAccepted());
		}
		submit(publicForm(UUID.randomUUID(), sanJose, "Misma", "7000-1111"), "192.0.2.99")
			.andExpect(status().isTooManyRequests());
		assertThat(countRows("service_requests")).isEqualTo(8);
	}

	@Test
	void aKnownPhoneIsNeverRevealedNorLinkedAutomatically() throws Exception {
		createUser("recepcion@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		mockMvc.perform(post("/api/v1/customers").session(login("recepcion@electronica-rojas.test"))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"branchId":%d,"customer":{"fullName":"Cliente Registrado","phone":"8888-7777"}}
					""".formatted(sanJose))).andExpect(status().isCreated());

		String known = submit(publicForm(UUID.randomUUID(), sanJose, "Cliente Registrado", "8888-7777"))
			.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
		String unknown = submit(publicForm(UUID.randomUUID(), sanJose, "Nadie", "7777-6666"))
			.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

		// Same shape and fields either way; no request gets a customer by itself.
		assertThat(JsonPath.<java.util.Map<String, Object>>read(known, "$").keySet())
			.isEqualTo(JsonPath.<java.util.Map<String, Object>>read(unknown, "$").keySet());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM service_requests WHERE customer_id IS NOT NULL", Integer.class))
			.isZero();
	}

	@Test
	void anonymousCallersReachNothingElse() throws Exception {
		mockMvc.perform(get("/api/v1/service-requests")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/customers/lookup").param("phone", "8888-7777")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/service-visits").param("from", "2030-01-01T00:00:00Z").param("to", "2030-01-02T00:00:00Z"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/public/service-requests/{ref}", UUID.randomUUID())).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/public/service-requests/not-a-uuid")).andExpect(status().isBadRequest());
	}

}
