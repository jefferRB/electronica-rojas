package dev.jeffrojas.electronicarojas.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;
import com.jayway.jsonpath.JsonPath;

/**
 * FR-CUS-001 / BR-CUS-001/002: normalization, duplicate detection without merging, branch scope
 * (same 404 for out of scope and missing), optimistic concurrency and audit without PII.
 */
class CustomerIntegrationTests extends IntegrationTestSupport {

	private long sanJose;

	private long alajuela;

	private MockHttpSession receptionist;

	@BeforeEach
	void setUp() throws Exception {
		sanJose = createBranch("SJ-01");
		alajuela = createBranch("AL-01");
		createUser("recepcion.sj@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		receptionist = login("recepcion.sj@electronica-rojas.test");
	}

	private ResultActions create(MockHttpSession session, long branchId, String name, String phone,
			boolean allowDuplicate) throws Exception {
		return mockMvc.perform(post("/api/v1/customers").session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"branchId":%d,"customer":{"fullName":"%s","phone":"%s","email":"Cliente@Ejemplo.test","allowDuplicatePhone":%s}}
					""".formatted(branchId, name, phone, allowDuplicate)));
	}

	private long createdId(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	@Test
	void createNormalizesPhoneAndNameAndAuditsWithoutPersonalData() throws Exception {
		long id = createdId(create(receptionist, sanJose, "  Ana   Pérez  ", "8888-7777", false)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.fullName").value("Ana Pérez"))
			.andExpect(jsonPath("$.phone").value("+50688887777"))
			.andExpect(jsonPath("$.email").value("cliente@ejemplo.test"))
			.andExpect(jsonPath("$.registeredBranch.code").value("SJ-01")));

		String audit = jdbc.queryForObject("SELECT action || ' ' || summary || ' ' || details::text FROM audit_events",
				String.class);
		assertThat(audit).startsWith("CUSTOMER_CREATED").doesNotContain("Ana", "8888", "ejemplo");
		assertThat(jdbc.queryForObject("SELECT entity_id FROM audit_events", String.class)).isEqualTo(String.valueOf(id));
	}

	@Test
	void invalidPhoneIsRejectedWithAStableCode() throws Exception {
		create(receptionist, sanJose, "Ana", "12345", false).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("phone"))
			.andExpect(jsonPath("$.errors[0].code").value("PHONE_INVALID"));
		assertThat(countRows("customers")).isZero();
	}

	@Test
	void samePhoneIsReportedAsPossibleDuplicateAndNeverMerged() throws Exception {
		create(receptionist, sanJose, "Ana Pérez", "8888-7777", false).andExpect(status().isCreated());

		create(receptionist, sanJose, "Ana P.", "+506 8888 7777", false).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("POSSIBLE_DUPLICATE_CUSTOMER"))
			.andExpect(jsonPath("$.matches", hasSize(1)))
			.andExpect(jsonPath("$.matches[0].fullName").value("Ana Pérez"))
			.andExpect(jsonPath("$.matches[0].inScope").value(true));
		assertThat(countRows("customers")).isEqualTo(1);

		// Explicit confirmation creates a second, independent record (e.g. a shared family phone).
		create(receptionist, sanJose, "Luis Pérez", "88887777", true).andExpect(status().isCreated());
		assertThat(countRows("customers")).isEqualTo(2);
	}

	@Test
	void customersOfOtherBranchesAreInvisibleButPhoneMatchIsMinimal() throws Exception {
		createUser("recepcion.al@electronica-rojas.test", Role.RECEPTIONIST, alajuela);
		MockHttpSession other = login("recepcion.al@electronica-rojas.test");
		long id = createdId(create(other, alajuela, "Marta Solís", "2222-3333", false));

		mockMvc.perform(get("/api/v1/customers/{id}", id).session(receptionist)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/customers/{id}", 999_999).session(receptionist)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/customers").param("search", "Marta").session(receptionist))
			.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/v1/customers/phone-matches").param("phone", "2222 3333").session(receptionist))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].inScope").value(false))
			.andExpect(jsonPath("$[0].phone").doesNotExist())
			.andExpect(jsonPath("$[0].email").doesNotExist());
		mockMvc.perform(put("/api/v1/customers/{id}", id).session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"X","phone":"22223333","version":0}
					""")).andExpect(status().isNotFound());
	}

	@Test
	void searchFindsByNameAndPhoneDigits() throws Exception {
		create(receptionist, sanJose, "Ana Pérez", "8888-7777", false);
		create(receptionist, sanJose, "Carlos Mora", "7000-1234", false);

		mockMvc.perform(get("/api/v1/customers").param("search", "pérez").session(receptionist))
			.andExpect(jsonPath("$.content[*].fullName").value(org.hamcrest.Matchers.contains("Ana Pérez")));
		mockMvc.perform(get("/api/v1/customers").param("search", "7000 1234").session(receptionist))
			.andExpect(jsonPath("$.content[*].fullName").value(org.hamcrest.Matchers.contains("Carlos Mora")));
	}

	@Test
	void updateUsesOptimisticVersion() throws Exception {
		long id = createdId(create(receptionist, sanJose, "Ana Pérez", "8888-7777", false));
		String body = """
				{"fullName":"Ana María Pérez","phone":"8888-7777","address":"Barrio Escalante","version":%d}
				""";

		mockMvc.perform(put("/api/v1/customers/{id}", id).session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body.formatted(0)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fullName").value("Ana María Pérez"))
			.andExpect(jsonPath("$.version").value(1));
		mockMvc.perform(put("/api/v1/customers/{id}", id).session(receptionist)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body.formatted(0)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"));
	}

	@Test
	void techniciansHaveNoCustomerModule() throws Exception {
		createUser("tecnico@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		MockHttpSession technician = login("tecnico@electronica-rojas.test");

		mockMvc.perform(get("/api/v1/customers").session(technician)).andExpect(status().isForbidden());
		create(technician, sanJose, "Ana", "8888-7777", false).andExpect(status().isForbidden());
	}

}
