package dev.jeffrojas.electronicarojas.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** Unit tests with a controllable clock: blocking, expiry (recovery) and reset on success. */
class LoginAttemptLimiterTest {

	/** Minimal mutable clock so the test can move time forward. */
	private static final class TestClock extends Clock {

		private Instant now = Instant.parse("2026-09-26T15:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

	private final TestClock clock = new TestClock();

	private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(
			new LoginThrottleProperties(5, 20, Duration.ofMinutes(15), Duration.ofMinutes(15)), clock);

	private void fail(String email, String ip, int times) {
		for (int i = 0; i < times; i++) {
			limiter.recordFailure(email, ip);
		}
	}

	@Test
	void blocksAnAccountAfterFiveFailuresAndReleasesItAfterTheLockDuration() {
		fail("user@electronica-rojas.test", "10.0.0.1", 4);
		assertThat(limiter.blockedFor("user@electronica-rojas.test", "10.0.0.1")).isEmpty();

		fail("user@electronica-rojas.test", "10.0.0.1", 1);
		assertThat(limiter.blockedFor("user@electronica-rojas.test", "10.0.0.2")).contains(Duration.ofMinutes(15));

		clock.advance(Duration.ofMinutes(14));
		assertThat(limiter.blockedFor("user@electronica-rojas.test", "10.0.0.2")).contains(Duration.ofMinutes(1));

		clock.advance(Duration.ofMinutes(1));
		assertThat(limiter.blockedFor("user@electronica-rojas.test", "10.0.0.2")).as("recovered").isEmpty();
	}

	@Test
	void emailsAreComparedNormalizedAndUnknownEmailsBehaveTheSame() {
		fail(" Nobody@Electronica-Rojas.TEST ", "10.0.0.1", 5);

		assertThat(limiter.blockedFor("nobody@electronica-rojas.test", "10.0.0.9")).isPresent();
	}

	@Test
	void successClearsTheAccountCounter() {
		fail("user@electronica-rojas.test", "10.0.0.1", 4);
		limiter.recordSuccess("USER@electronica-rojas.test");
		fail("user@electronica-rojas.test", "10.0.0.1", 4);

		assertThat(limiter.blockedFor("user@electronica-rojas.test", "10.0.0.1")).isEmpty();
	}

	@Test
	void failuresOutsideTheWindowDoNotAccumulate() {
		fail("user@electronica-rojas.test", "10.0.0.1", 4);
		clock.advance(Duration.ofMinutes(16));
		fail("user@electronica-rojas.test", "10.0.0.1", 4);

		assertThat(limiter.blockedFor("user@electronica-rojas.test", "10.0.0.1")).isEmpty();
	}

	@Test
	void oneClientTryingManyAccountsIsBlockedByAddress() {
		for (int i = 0; i < 20; i++) {
			limiter.recordFailure("user" + i + "@electronica-rojas.test", "10.0.0.66");
		}

		assertThat(limiter.blockedFor("fresh@electronica-rojas.test", "10.0.0.66")).isPresent();
		assertThat(limiter.blockedFor("fresh@electronica-rojas.test", "10.0.0.67")).isEmpty();
	}

}
