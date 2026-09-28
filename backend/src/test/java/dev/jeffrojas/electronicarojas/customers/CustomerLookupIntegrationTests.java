package dev.jeffrojas.electronicarojas.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;
import com.jayway.jsonpath.JsonPath;

/**
 * FR-CUS-002 (Phase 3.1): incremental lookup by name and phone, identity resolution on save and
 * branch scope. All names and numbers are fictitious.
 */
class CustomerLookupIntegrationTests extends IntegrationTestSupport {

	private long sanJose;

	private long alajuela;

	private MockHttpSession receptionist;

	private MockHttpSession alajuelaReceptionist;

	@BeforeEach
	void setUp() throws Exception {
		sanJose = createBranch("SJ-01");
		alajuela = createBranch("AL-01");
		createUser("recepcion@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		createUser("recepcion.al@electronica-rojas.test", Role.RECEPTIONIST, alajuela);
		receptionist = login("recepcion@electronica-rojas.test");
		alajuelaReceptionist = login("recepcion.al@electronica-rojas.test");
	}

	private ResultActions create(MockHttpSession session, long branchId, String name, String phone, boolean confirmed)
			throws Exception {
		return mockMvc.perform(post("/api/v1/customers").session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"branchId":%d,"customer":{"fullName":"%s","phone":"%s","email":"cliente@ejemplo.test","allowDuplicatePhone":%s}}
					""".formatted(branchId, name, phone, confirmed)));
	}

	private long created(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
				"$.id")).longValue();
	}

	private ResultActions lookup(MockHttpSession session, String name, String phone) throws Exception {
		var request = get("/api/v1/customers/lookup").session(session);
		if (name != null) {
			request.param("name", name);
		}
		if (phone != null) {
			request.param("phone", phone);
		}
		return mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void findsByPartialNameIgnoringCaseAccentsSpacesAndWordOrder() throws Exception {
		created(create(receptionist, sanJose, "Ana María  Pérez Solís", "8888-7777", false));
		assertThat(jdbc.queryForObject("SELECT search_name FROM customers", String.class))
			.isEqualTo("ana maria perez solis");

		for (String term : new String[] { "perez", "PÉREZ", "  ana   maria ", "solis ana", "Pér" }) {
			lookup(receptionist, term, null).andExpect(jsonPath("$.candidates[*].fullName").value(contains("Ana María Pérez Solís")));
		}
		lookup(receptionist, "gomez", null).andExpect(jsonPath("$.candidates", hasSize(0)));
		// Fewer than 3 letters: no search at all.
		lookup(receptionist, "an", null).andExpect(jsonPath("$.candidates", hasSize(0)));
	}

	@Test
	void findsByPhoneFragmentFromThreeDigits() throws Exception {
		created(create(receptionist, sanJose, "Ana Pérez", "8888-7777", false));

		lookup(receptionist, null, "8877").andExpect(jsonPath("$.candidates[0].phone").value("+50688887777"))
			.andExpect(jsonPath("$.candidates[0].phoneMatch").value(true))
			.andExpect(jsonPath("$.candidates[0].inScope").value(true));
		lookup(receptionist, null, "888").andExpect(jsonPath("$.candidates", hasSize(1)));
		lookup(receptionist, null, "+506 8888-7777").andExpect(jsonPath("$.candidates", hasSize(1)));
		lookup(receptionist, null, "88").andExpect(jsonPath("$.candidates", hasSize(0)));
	}

	@Test
	void customersMatchingNameAndPhoneComeFirst() throws Exception {
		created(create(receptionist, sanJose, "Ana Pérez", "8888-7777", false));
		created(create(receptionist, sanJose, "Ana Rojas", "7000-1234", false));

		lookup(receptionist, "ana", "7000").andExpect(jsonPath("$.candidates", hasSize(2)))
			.andExpect(jsonPath("$.candidates[0].fullName").value("Ana Rojas"))
			.andExpect(jsonPath("$.candidates[0].nameMatch").value(true))
			.andExpect(jsonPath("$.candidates[0].phoneMatch").value(true))
			.andExpect(jsonPath("$.candidates[1].phoneMatch").value(false));
	}

	@Test
	void partialTermsNeverRevealOtherBranchesButACompletePhoneGivesMinimalIdentity() throws Exception {
		long marta = created(create(alajuelaReceptionist, alajuela, "Marta Solís", "2222-3333", false));

		lookup(receptionist, "marta", null).andExpect(jsonPath("$.candidates", hasSize(0)));
		lookup(receptionist, null, "2222").andExpect(jsonPath("$.candidates", hasSize(0)));
		lookup(receptionist, "sol", "2222-333").andExpect(jsonPath("$.candidates", hasSize(0)));

		lookup(receptionist, null, "2222 3333").andExpect(jsonPath("$.candidates", hasSize(1)))
			.andExpect(jsonPath("$.candidates[0].id").value(marta))
			.andExpect(jsonPath("$.candidates[0].fullName").value("Marta Solís"))
			.andExpect(jsonPath("$.candidates[0].inScope").value(false))
			.andExpect(jsonPath("$.candidates[0].phone").doesNotExist())
			.andExpect(jsonPath("$.candidates[0].email").doesNotExist())
			.andExpect(jsonPath("$.candidates[0].registeredBranch").doesNotExist());
		// Reading the record still requires scope.
		mockMvc.perform(get("/api/v1/customers/{id}", marta).session(receptionist)).andExpect(status().isNotFound());
	}

	@Test
	void relativesSharingAPhoneAreAllListed() throws Exception {
		created(create(receptionist, sanJose, "Ana Pérez", "8888-7777", false));
		created(create(receptionist, sanJose, "Luis Pérez", "8888-7777", true));

		lookup(receptionist, null, "88887777")
			.andExpect(jsonPath("$.candidates[*].fullName").value(containsInAnyOrder("Ana Pérez", "Luis Pérez")));
		lookup(receptionist, "luis", "8888-7777").andExpect(jsonPath("$.candidates[0].fullName").value("Luis Pérez"));
	}

	@Test
	void resultsAreLimitedAndReportHowManyMoreExist() throws Exception {
		for (int i = 1; i <= 12; i++) {
			created(create(receptionist, sanJose, "Cliente Prueba %02d".formatted(i), "8000-%04d".formatted(i), false));
		}
		lookup(receptionist, "cliente prueba", null).andExpect(jsonPath("$.candidates", hasSize(8)))
			.andExpect(jsonPath("$.moreInScope").value(4))
			.andExpect(jsonPath("$.candidates[0].fullName").value("Cliente Prueba 01"));
		mockMvc.perform(get("/api/v1/customers/lookup").param("name", "cliente").param("size", "11").session(receptionist))
			.andExpect(status().isBadRequest());
	}

	@Test
	void sameNameOrPhoneIsReportedBeforeCreatingAndCanBeConfirmed() throws Exception {
		long ana = created(create(receptionist, sanJose, "Ana Pérez", "8888-7777", false));

		create(receptionist, sanJose, "ANA  PEREZ", "7000-1111", false).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("POSSIBLE_DUPLICATE_CUSTOMER"))
			.andExpect(jsonPath("$.matches[0].id").value(ana))
			.andExpect(jsonPath("$.matches[0].matchedBy").value("NAME"));
		create(receptionist, sanJose, "Ana Pérez", "8888 7777", false).andExpect(status().isConflict())
			.andExpect(jsonPath("$.matches[0].matchedBy").value("PHONE_AND_NAME"));
		create(receptionist, sanJose, "Otra Persona", "8888 7777", false).andExpect(status().isConflict())
			.andExpect(jsonPath("$.matches[0].matchedBy").value("PHONE"));
		assertThat(countRows("customers")).isEqualTo(1);

		created(create(receptionist, sanJose, "ANA  PEREZ", "7000-1111", true));
		assertThat(countRows("customers")).isEqualTo(2);
	}

	@Test
	void aNameOfAnotherBranchIsNotReportedAsDuplicate() throws Exception {
		created(create(alajuelaReceptionist, alajuela, "Marta Solís", "2222-3333", false));
		// Reporting it would reveal that a "Marta Solís" exists elsewhere; only an exact phone does.
		created(create(receptionist, sanJose, "Marta Solís", "7000-2222", false));
	}

	@Test
	void receptionWithADuplicateNewCustomerStoresNothingAndAnExistingOneIsLinked() throws Exception {
		long ana = created(create(receptionist, sanJose, "Ana Pérez", "8888-7777", false));
		String order = """
				{"operationId":"%s","branchId":%d,%s,
				 "deviceType":"Lavadora","brand":"LG","reportedFault":"No centrifuga","physicalCondition":"Bueno"}
				""";

		mockMvc.perform(post("/api/v1/repair-orders").session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(order.formatted(UUID.randomUUID(), sanJose,
					"\"newCustomer\":{\"fullName\":\"Ana Perez\",\"phone\":\"88887777\"}")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("POSSIBLE_DUPLICATE_CUSTOMER"));
		assertThat(countRows("customers")).isEqualTo(1);
		assertThat(countRows("repair_orders")).isZero();

		mockMvc.perform(post("/api/v1/repair-orders").session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(order.formatted(UUID.randomUUID(), sanJose, "\"customerId\":" + ana)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.customer.id").value(ana));
		assertThat(countRows("customers")).isEqualTo(1);
	}

	@Test
	void techniciansCannotSearchCustomers() throws Exception {
		createUser("tecnico@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		mockMvc.perform(get("/api/v1/customers/lookup").param("name", "ana").session(login("tecnico@electronica-rojas.test")))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/customers/lookup").param("name", "ana")).andExpect(status().isUnauthorized());
	}

}
