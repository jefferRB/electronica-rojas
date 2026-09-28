package dev.jeffrojas.electronicarojas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.json.JsonCompareMode;

/**
 * Full application context against real PostgreSQL 17 in Docker (E2E-01), served by a real
 * embedded Tomcat on a random port so container behaviour (error forwards) is covered too.
 * Requires a running Docker Engine; run Docker-free tests with -DexcludedGroups=integration.
 */
class ElectronicaRojasApplicationTests extends IntegrationTestSupport {

	@LocalServerPort
	private int port;

	@Test
	void contextLoads() {
	}

	@Test
	void flywayAppliedAllMigrations() {
		List<Map<String, Object>> applied = jdbc.queryForList(
				"SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

		assertThat(applied).extracting(row -> row.get("version")).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
		assertThat(applied).allSatisfy(row -> assertThat(row).containsEntry("success", true));
	}

	@Test
	void healthIsPublicAndSanitized() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
	}

	@Test
	void otherActuatorEndpointsAreNotPublic() throws Exception {
		mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/actuator/health/db")).andExpect(status().isUnauthorized());
	}

	/**
	 * Regression: over real HTTP the 403 from the CSRF filter is forwarded to /error. That
	 * forward must not be re-authorized, or the anonymous caller receives 401 instead of 403.
	 * MockMvc does not perform error forwards, so this needs the real server.
	 */
	@Test
	void mutationWithoutCsrfTokenIsForbiddenOverRealHttp() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/ping"))
			.POST(HttpRequest.BodyPublishers.noBody())
			.build();

		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(403);
			assertThat(response.body()).doesNotContain("Exception", "trace");
		}
	}

}
