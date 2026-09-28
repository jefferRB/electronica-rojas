package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Component;

/**
 * Abuse protection of the public form (BR-SRV-004), in memory like the login limiter (one
 * instance, no Redis - ADR-006): at most N accepted submissions per client address per window and
 * M per phone number per window. Replays of an already accepted submission do not count.
 * Synchronized: the public form is low traffic and the critical sections are tiny.
 */
@Component
@EnableConfigurationProperties(PublicRequestLimiter.Limits.class)
public class PublicRequestLimiter {

	@ConfigurationProperties("app.public.requests")
	public record Limits(@DefaultValue("5") int perClient, @DefaultValue("PT1H") Duration clientWindow,
			@DefaultValue("3") int perPhone, @DefaultValue("P1D") Duration phoneWindow) {
	}

	private final Limits limits;

	private final Clock clock;

	private final Map<String, Deque<Instant>> byClient = new HashMap<>();

	private final Map<String, Deque<Instant>> byPhone = new HashMap<>();

	PublicRequestLimiter(Limits limits, Clock clock) {
		this.limits = limits;
		this.clock = clock;
	}

	/** How long the caller must wait if a limit is reached; empty when the submission may go on. */
	synchronized Optional<Duration> blockedFor(String clientAddress, String phone) {
		Instant now = clock.instant();
		Optional<Duration> client = wait(byClient, clientAddress, limits.perClient(), limits.clientWindow(), now);
		return client.isPresent() ? client : wait(byPhone, phone, limits.perPhone(), limits.phoneWindow(), now);
	}

	/** Counts an accepted submission. */
	synchronized void recordAccepted(String clientAddress, String phone) {
		Instant now = clock.instant();
		byClient.computeIfAbsent(clientAddress, key -> new ArrayDeque<>()).addLast(now);
		byPhone.computeIfAbsent(phone, key -> new ArrayDeque<>()).addLast(now);
		byClient.values().removeIf(hits -> expire(hits, limits.clientWindow(), now));
		byPhone.values().removeIf(hits -> expire(hits, limits.phoneWindow(), now));
	}

	/** Test support: forget every counter. */
	public synchronized void reset() {
		byClient.clear();
		byPhone.clear();
	}

	private static Optional<Duration> wait(Map<String, Deque<Instant>> counters, String key, int max, Duration window,
			Instant now) {
		Deque<Instant> hits = counters.get(key);
		if (hits == null || expire(hits, window, now) || hits.size() < max) {
			return Optional.empty();
		}
		return Optional.of(Duration.between(now, hits.peekFirst().plus(window)));
	}

	/** Drops hits older than the window; true when nothing is left. */
	private static boolean expire(Deque<Instant> hits, Duration window, Instant now) {
		while (!hits.isEmpty() && !hits.peekFirst().isAfter(now.minus(window))) {
			hits.pollFirst();
		}
		return hits.isEmpty();
	}

}
