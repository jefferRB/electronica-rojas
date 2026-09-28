package dev.jeffrojas.electronicarojas;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Disposable PostgreSQL 17 for integration tests (ARCH-DEV-005).
 * <p>
 * {@code @ServiceConnection} publishes the container's JDBC URL and credentials as
 * connection details, so the DataSource and Flyway use it instead of DB_URL/DB_*.
 * Spring starts the container with the context and stops it on shutdown.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

}
