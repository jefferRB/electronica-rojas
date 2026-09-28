package dev.jeffrojas.electronicarojas.shared;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single source of "now" for the application. Services depend on {@link Clock} instead of
 * calling {@code Instant.now()} so tests can pin time. Instants are UTC (DATA-006).
 * <p>
 * Ticks in microseconds, the precision of PostgreSQL {@code timestamptz}: a value returned in a
 * response is identical to the one read back from the database later (e.g. an idempotent replay).
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	/** Zone of the business: calendar-day filters and codes use it; storage stays UTC (BR-DAT-002). */
	public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Costa_Rica");

	@Bean
	Clock clock() {
		return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
	}

}
