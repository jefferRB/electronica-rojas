package dev.jeffrojas.electronicarojas.shared.ping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** Pure unit test: no Spring context, no Docker. */
class PingServiceTest {

	@Test
	void returnsOkWithCurrentUtcInstant() {
		Instant now = Instant.parse("2026-09-25T18:00:00Z");
		PingService service = new PingService(Clock.fixed(now, ZoneOffset.UTC));

		PingResponse response = service.ping();

		assertThat(response.status()).isEqualTo("ok");
		assertThat(response.timestamp()).isEqualTo(now);
	}

}
