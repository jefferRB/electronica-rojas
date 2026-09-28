package dev.jeffrojas.electronicarojas.servicerequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import dev.jeffrojas.electronicarojas.security.Role;
import com.jayway.jsonpath.JsonPath;

/**
 * BR-SRV-009: the shareable public portal. ADMIN configures it; the anonymous page gets only what it
 * needs to render; a paused portal keeps its address and refuses new requests; previous slugs keep
 * resolving; the form follows the configured rules.
 */
class PortalSettingsIntegrationTests extends HomeServiceTestSupport {

	private static final String SETTINGS = """
			{"enabled":%s,"allowPreferredDate":true,"allowPreferredWindow":true,"minNoticeDays":%d,"maxDaysAhead":30,
			 "serviceDays":[5,1,2,3,4,1],"servedProvinces":["HEREDIA","SAN_JOSE"],
			 "serviceTypes":["Lavadora"," Refrigeradora ","lavadora"],
			 "welcomeMessage":"Contanos qué equipo necesitás reparar.","successMessage":"Te contactaremos pronto.",
			 "version":%d}
			""";

	private long sanJose;

	private MockHttpSession admin;

	@BeforeEach
	void setUp() throws Exception {
		sanJose = createBranch("SJ-01");
		admin = loginAsAdmin();
	}

	private ResultActions saveSettings(MockHttpSession session, String json) throws Exception {
		return mockMvc.perform(put("/api/v1/portal-settings").session(session).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private ResultActions changeSlug(String slug, long version) throws Exception {
		return mockMvc.perform(put("/api/v1/portal-settings/slug").session(admin).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"slug\":\"%s\",\"version\":%d}".formatted(slug, version)));
	}

	private ResultActions submit(String json) throws Exception {
		return mockMvc.perform(post("/api/v1/public/service-requests").with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private static LocalDate crToday() {
		return LocalDate.now(ZoneId.of("America/Costa_Rica"));
	}

	@Test
	void adminReadsAndSavesTheSettingsAndTheChangeIsAudited() throws Exception {
		mockMvc.perform(get("/api/v1/portal-settings").session(admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled").value(true))
			.andExpect(jsonPath("$.slug").value("servicio-a-domicilio"))
			.andExpect(jsonPath("$.maxDaysAhead").value(90));

		saveSettings(admin, SETTINGS.formatted(true, 1, 0)).andExpect(status().isOk())
			// Weekdays sorted without repeats, provinces in their fixed order, types trimmed and unique.
			.andExpect(jsonPath("$.serviceDays").value(contains(1, 2, 3, 4, 5)))
			.andExpect(jsonPath("$.servedProvinces").value(contains("SAN_JOSE", "HEREDIA")))
			.andExpect(jsonPath("$.serviceTypes").value(contains("Lavadora", "Refrigeradora")))
			.andExpect(jsonPath("$.updatedBy.fullName").exists())
			.andExpect(jsonPath("$.version").value(1));

		assertThat(jdbc.queryForObject("SELECT details->>'changedFields' FROM audit_events WHERE action = 'PUBLIC_PORTAL_UPDATED'",
				String.class)).contains("minNoticeDays", "serviceDays", "welcomeMessage").doesNotContain("enabled\"");
		// A stale version is refused.
		saveSettings(admin, SETTINGS.formatted(true, 1, 0)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STALE_VERSION"));
	}

	@Test
	void onlyAdministratorsConfigureThePortal() throws Exception {
		createUser("gerente@electronica-rojas.test", Role.BRANCH_MANAGER, sanJose);
		createUser("recepcion@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		createUser("tecnico@electronica-rojas.test", Role.TECHNICIAN, sanJose);

		for (String email : new String[] { "gerente@electronica-rojas.test", "recepcion@electronica-rojas.test", "tecnico@electronica-rojas.test" }) {
			MockHttpSession session = login(email);
			mockMvc.perform(get("/api/v1/portal-settings").session(session)).andExpect(status().isForbidden());
			saveSettings(session, SETTINGS.formatted(false, 0, 0)).andExpect(status().isForbidden());
		}
		mockMvc.perform(get("/api/v1/portal-settings")).andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/v1/portal-settings/slug").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"slug\":\"otro-portal\",\"version\":0}")).andExpect(status().isUnauthorized());
		// Without CSRF even the administrator cannot change it.
		mockMvc.perform(put("/api/v1/portal-settings").session(admin).contentType(MediaType.APPLICATION_JSON)
			.content(SETTINGS.formatted(false, 0, 0))).andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT enabled FROM public_portal_settings", Boolean.class)).isTrue();
	}

	@Test
	void theAnonymousPageGetsOnlyWhatItRenders() throws Exception {
		saveSettings(admin, SETTINGS.formatted(true, 2, 0)).andExpect(status().isOk());

		String body = mockMvc.perform(get("/api/v1/public/portal/{slug}", "servicio-a-domicilio"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("servicio-a-domicilio"))
			.andExpect(jsonPath("$.accepting").value(true))
			.andExpect(jsonPath("$.welcomeMessage").value("Contanos qué equipo necesitás reparar."))
			.andExpect(jsonPath("$.rules.earliestDate").value(crToday().plusDays(2).toString()))
			.andExpect(jsonPath("$.rules.latestDate").value(crToday().plusDays(30).toString()))
			.andExpect(jsonPath("$.rules.serviceTypes").value(contains("Lavadora", "Refrigeradora")))
			.andExpect(jsonPath("$.branches[0].name").value("Sucursal SJ-01"))
			.andExpect(jsonPath("$.branches[0].code").doesNotExist())
			.andExpect(jsonPath("$.version").doesNotExist())
			.andExpect(jsonPath("$.updatedBy").doesNotExist())
			.andExpect(jsonPath("$.previousSlugs").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain("admin@", "Administrador");

		mockMvc.perform(get("/api/v1/public/portal/{slug}", "no-existe")).andExpect(status().isNotFound());
		// Old links without a slug still find the current portal.
		mockMvc.perform(get("/api/v1/public/portal")).andExpect(jsonPath("$.slug").value("servicio-a-domicilio"));
		// The anonymous surface stays small: no request list, no settings.
		mockMvc.perform(get("/api/v1/service-requests")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
	}

	@Test
	void aPausedPortalKeepsItsAddressAndRefusesNewRequestsOnly() throws Exception {
		UUID earlier = UUID.randomUUID();
		String earlierReceipt = submit(publicForm(earlier, sanJose, "Ana", "8888-7777")).andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();

		saveSettings(admin, SETTINGS.formatted(false, 0, 0)).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT details->>'enabledChanged' FROM audit_events WHERE action = 'PUBLIC_PORTAL_UPDATED'",
				String.class)).isEqualTo("true");

		mockMvc.perform(get("/api/v1/public/portal/{slug}", "servicio-a-domicilio"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accepting").value(false))
			.andExpect(jsonPath("$.rules").doesNotExist())
			.andExpect(jsonPath("$.branches").isEmpty());
		submit(publicForm(UUID.randomUUID(), sanJose, "Luis", "8888-6666")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PORTAL_DISABLED"));
		// A retry of a request sent before the pause still gets its receipt; nothing was deleted.
		String replay = submit(publicForm(earlier, sanJose, "Ana", "8888-7777")).andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat((String) JsonPath.read(replay, "$.publicRef")).isEqualTo(JsonPath.read(earlierReceipt, "$.publicRef"));
		assertThat(countRows("service_requests")).isEqualTo(1);
	}

	@Test
	void theFormFollowsTheConfiguredRules() throws Exception {
		saveSettings(admin, SETTINGS.formatted(true, 2, 0)).andExpect(status().isOk());
		String form = publicForm(UUID.randomUUID(), sanJose, "Ana", "8888-7777");

		submit(form.replace("\"SAN_JOSE\"", "\"LIMON\"")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("PROVINCE_NOT_SERVED"));
		submit(form.replace("\"preferredWindow\"", "\"preferredDate\":\"%s\",\"preferredWindow\"".formatted(crToday())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("DATE_TOO_SOON"));
		LocalDate saturday = crToday().plusDays(3);
		while (saturday.getDayOfWeek().getValue() != 6) {
			saturday = saturday.plusDays(1);
		}
		submit(form.replace("\"preferredWindow\"", "\"preferredDate\":\"%s\",\"preferredWindow\"".formatted(saturday)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("DAY_NOT_SERVED"));
		assertThat(countRows("service_requests")).isZero();

		// Heredia is served: the request is received as a request, not as a visit.
		submit(form.replace("\"SAN_JOSE\"", "\"HEREDIA\"")).andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("RECEIVED"));
		assertThat(countRows("service_visits")).isZero();
	}

	@Test
	void messagesArePlainTextOnly() throws Exception {
		saveSettings(admin, SETTINGS.formatted(true, 0, 0).replace("Te contactaremos pronto.",
				"<script>alert(1)</script>")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("HTML_NOT_ALLOWED"));
		saveSettings(admin, SETTINGS.formatted(true, 0, 0).replace("Te contactaremos pronto.", "x".repeat(501)))
			.andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("SELECT success_message FROM public_portal_settings", String.class)).isNull();
	}

	@Test
	void slugsAreValidatedAndAPreviousSlugKeepsWorking() throws Exception {
		changeSlug("Mi Portal", 0).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("SLUG_INVALID"));
		changeSlug("ab", 0).andExpect(status().isBadRequest());
		changeSlug("dos--guiones", 0).andExpect(status().isBadRequest());
		changeSlug("admin", 0).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("SLUG_RESERVED"));
		// The same address is not a change.
		changeSlug("servicio-a-domicilio", 0).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));

		changeSlug(" Electronica-Perez ", 0).andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("electronica-perez"))
			.andExpect(jsonPath("$.previousSlugs").value(contains("servicio-a-domicilio")));

		// A printed QR with the old address opens the portal and learns the current slug.
		mockMvc.perform(get("/api/v1/public/portal/{slug}", "servicio-a-domicilio"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("electronica-perez"));
		mockMvc.perform(get("/api/v1/public/portal/{slug}", "electronica-perez")).andExpect(status().isOk());
		assertThat(jdbc.queryForMap("""
				SELECT details->>'previousSlug' AS previous, details->>'newSlug' AS current
				FROM audit_events WHERE action = 'PUBLIC_PORTAL_SLUG_CHANGED'
				""")).containsEntry("previous", "servicio-a-domicilio").containsEntry("current", "electronica-perez");

		// Stale version, and one slug per portal in the database.
		changeSlug("otra-direccion", 0).andExpect(status().isConflict());
		assertThat(jdbc.queryForObject("SELECT count(DISTINCT slug) FROM public_portal_settings", Integer.class)).isEqualTo(1);
		org.assertj.core.api.Assertions
			.assertThatThrownBy(() -> jdbc.update("UPDATE public_portal_settings SET slug = 'Con Espacio'"))
			.hasMessageContaining("ck_public_portal_settings_slug");
		org.assertj.core.api.Assertions
			.assertThatThrownBy(() -> jdbc.update("INSERT INTO public_portal_settings (id, enabled, slug, allow_preferred_date, "
					+ "allow_preferred_window, min_notice_days, max_days_ahead, service_days, served_provinces, service_types, "
					+ "updated_at) VALUES (2, TRUE, 'segundo', TRUE, TRUE, 0, 1, '[]', '[]', '[]', now())"))
			.hasMessageContaining("ck_public_portal_settings_single");
	}

}
