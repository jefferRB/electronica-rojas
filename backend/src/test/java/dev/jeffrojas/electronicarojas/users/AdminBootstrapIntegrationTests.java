package dev.jeffrojas.electronicarojas.users;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;

/** SEC-004: the bootstrap administrator is created once from environment configuration. */
class AdminBootstrapIntegrationTests extends IntegrationTestSupport {

	@Autowired
	private AdminBootstrap adminBootstrap;

	@Test
	void bootstrapAdministratorExistsAndCanLogIn() throws Exception {
		assertThat(jdbc.queryForObject("SELECT role FROM app_users WHERE email = ?", String.class, ADMIN_EMAIL))
			.isEqualTo("ADMIN");

		loginAsAdmin();
	}

	@Test
	void runningTheBootstrapAgainChangesNothing() throws Exception {
		String hashBefore = passwordHashOfAdmin();

		adminBootstrap.run(null);
		adminBootstrap.run(null);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users WHERE role = 'ADMIN'", Integer.class)).isEqualTo(1);
		assertThat(passwordHashOfAdmin()).isEqualTo(hashBefore);
	}

	private String passwordHashOfAdmin() {
		return jdbc.queryForObject("SELECT password_hash FROM app_users WHERE email = ?", String.class, ADMIN_EMAIL);
	}

}
