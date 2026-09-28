package dev.jeffrojas.electronicarojas.servicerequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import dev.jeffrojas.electronicarojas.security.Role;
import com.jayway.jsonpath.JsonPath;

/** FR-SRV-003: inbox scope, identity resolution, branch changes, rejection and cancellation. */
class ServiceRequestIntegrationTests extends HomeServiceTestSupport {

	private long sanJose;

	private long alajuela;

	private MockHttpSession receptionist;

	private MockHttpSession manager;

	@BeforeEach
	void setUp() throws Exception {
		sanJose = createBranch("SJ-01");
		alajuela = createBranch("AL-01");
		createUser("recepcion@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		createUser("gerente@electronica-rojas.test", Role.BRANCH_MANAGER, sanJose);
		receptionist = login("recepcion@electronica-rojas.test");
		manager = login("gerente@electronica-rojas.test");
	}

	private long submitPublic(long branchId, String name, String phone) throws Exception {
		mockMvc.perform(post("/api/v1/public/service-requests").with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(publicForm(UUID.randomUUID(), branchId, name, phone))).andExpect(status().isAccepted());
		return jdbc.queryForObject("SELECT max(id) FROM service_requests", Long.class);
	}

	private ResultActions link(MockHttpSession session, long requestId, String json) throws Exception {
		return mockMvc.perform(put("/api/v1/service-requests/{id}/customer", requestId).session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private ResultActions decide(MockHttpSession session, long requestId, String action, String reason) throws Exception {
		return mockMvc.perform(post("/api/v1/service-requests/{id}/" + action, requestId).session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"%s\"}".formatted(reason)));
	}

	@Test
	void theInboxShowsOnlyRequestsOfTheUsersBranches() throws Exception {
		long mine = submitPublic(sanJose, "Ana Pérez", "8888-7777");
		long other = submitPublic(alajuela, "Luis Mora", "7000-1234");
		createUser("tecnico@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		MockHttpSession technician = login("tecnico@electronica-rojas.test");

		mockMvc.perform(get("/api/v1/service-requests").session(receptionist))
			.andExpect(jsonPath("$.content[*].id").value(contains((int) mine)))
			.andExpect(jsonPath("$.content[0].status").value("PENDING"))
			.andExpect(jsonPath("$.content[0].activeVisit").doesNotExist());
		mockMvc.perform(get("/api/v1/service-requests/{id}", other).session(receptionist)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/service-requests/{id}", mine).session(receptionist))
			.andExpect(jsonPath("$.contactPhone").value("+50688887777"))
			.andExpect(jsonPath("$.actions.canLinkCustomer").value(true))
			.andExpect(jsonPath("$.actions.canSchedule").value(false));
		mockMvc.perform(get("/api/v1/service-requests").session(technician)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/service-requests").session(loginAsAdmin())).andExpect(jsonPath("$.totalElements").value(2));
	}

	@Test
	void identityResolutionLinksExistingOrNewCustomersWithoutDuplicates() throws Exception {
		long requestId = submitPublic(sanJose, "Ana Pérez", "8888-7777");
		String created = mockMvc.perform(post("/api/v1/customers").session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"branchId":%d,"customer":{"fullName":"Ana Pérez","phone":"8888-7777"}}
					""".formatted(sanJose))).andReturn().getResponse().getContentAsString();
		long ana = ((Number) JsonPath.read(created, "$.id")).longValue();

		// Registering "new" finds the existing record by phone and name first.
		link(receptionist, requestId, "{\"registerNew\":true}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("POSSIBLE_DUPLICATE_CUSTOMER"))
			.andExpect(jsonPath("$.matches[0].matchedBy").value("PHONE_AND_NAME"));
		link(receptionist, requestId, "{\"customerId\":%d}".formatted(ana)).andExpect(status().isOk())
			.andExpect(jsonPath("$.customer.id").value(ana))
			.andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
			.andExpect(jsonPath("$.actions.canSchedule").value(true));
		assertThat(countRows("customers")).isEqualTo(1);
		link(receptionist, requestId, "{\"registerNew\":true,\"confirmedNewPerson\":true}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CUSTOMER_ALREADY_LINKED"));

		long second = submitPublic(sanJose, "Marta Solís", "2222-3333");
		link(receptionist, second, "{\"registerNew\":true}").andExpect(status().isOk())
			.andExpect(jsonPath("$.customer.fullName").value("Marta Solís"));
		assertThat(countRows("customers")).isEqualTo(2);
		assertThat(jdbc.queryForList("SELECT event_type FROM service_request_events WHERE request_id = ? ORDER BY id",
				String.class, second)).containsExactly("SUBMITTED", "REVIEW_STARTED", "CUSTOMER_LINKED");
	}

	@Test
	void aCustomerOfAnotherBranchIsLinkedOnlyThroughTheRequestsOwnPhone() throws Exception {
		createUser("recepcion.al@electronica-rojas.test", Role.RECEPTIONIST, alajuela);
		String created = mockMvc.perform(post("/api/v1/customers").session(login("recepcion.al@electronica-rojas.test"))
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"branchId":%d,"customer":{"fullName":"Luis Mora","phone":"7000-1234"}}
					""".formatted(alajuela))).andReturn().getResponse().getContentAsString();
		long luis = ((Number) JsonPath.read(created, "$.id")).longValue();

		long otherPhone = submitPublic(sanJose, "Luis Mora", "7000-9999");
		link(receptionist, otherPhone, "{\"customerId\":%d}".formatted(luis)).andExpect(status().isNotFound());
		long samePhone = submitPublic(sanJose, "Luis Mora", "7000-1234");
		link(receptionist, samePhone, "{\"customerId\":%d}".formatted(luis)).andExpect(status().isOk());
	}

	@Test
	void rejectionNeedsAReasonAndIsFinal() throws Exception {
		long requestId = submitPublic(sanJose, "Ana Pérez", "8888-7777");
		decide(receptionist, requestId, "reject", " ").andExpect(status().isBadRequest());
		decide(receptionist, requestId, "reject", "Fuera de la zona de cobertura").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.decisionReason").value("Fuera de la zona de cobertura"))
			.andExpect(jsonPath("$.history[1].type").value("REJECTED"))
			.andExpect(jsonPath("$.history[1].actor.fullName").value("Usuario RECEPTIONIST"));
		decide(receptionist, requestId, "cancel", "x").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
		link(receptionist, requestId, "{\"registerNew\":true}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REQUEST_CLOSED"));
	}

	@Test
	void onlyManagementMovesARequestToAnotherBranch() throws Exception {
		long requestId = submitPublic(sanJose, "Ana Pérez", "8888-7777");
		String body = "{\"branchId\":%d}".formatted(alajuela);
		mockMvc.perform(put("/api/v1/service-requests/{id}/branch", requestId).session(receptionist)
			.with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
		// The manager moves it out of their own scope: 204, and they no longer see it.
		mockMvc.perform(put("/api/v1/service-requests/{id}/branch", requestId).session(manager)
			.with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/service-requests/{id}", requestId).session(manager)).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT details->>'previousBranchCode' FROM service_request_events WHERE event_type = 'BRANCH_CHANGED'",
				String.class)).isEqualTo("SJ-01");
	}

	@Test
	void staffRequestsResolveTheCustomerInTheSameTransaction() throws Exception {
		long requestId = staffRequest(receptionist, sanJose, "Carlos Rojas", "8111-2222");
		mockMvc.perform(get("/api/v1/service-requests/{id}", requestId).session(receptionist))
			.andExpect(jsonPath("$.channel").value("STAFF"))
			.andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
			.andExpect(jsonPath("$.customer.fullName").value("Carlos Rojas"))
			.andExpect(jsonPath("$.createdBy.fullName").value("Usuario RECEPTIONIST"));
		assertThat(countRows("customers")).isEqualTo(1);
	}

}
