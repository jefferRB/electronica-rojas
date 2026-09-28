package dev.jeffrojas.electronicarojas.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory brute-force protection for the login form (SEC-006), sized for the single-instance
 * monolith (ARCH-SEC-006): no Redis. With several instances each would count on its own; a shared
 * store would be needed then.
 * <ul>
 * <li>Per account: the normalized email is counted even if no such account exists, so a block
 * says nothing about which emails are registered.</li>
 * <li>Per client address: slows down trying many emails from one place.</li>
 * </ul>
 * A successful login clears the account's failures. Entries expire on their own; the maps are
 * pruned when they grow, so an attacker cannot exhaust memory with random emails.
 */
@Component
public class LoginAttemptLimiter {

	private static final int PRUNE_THRESHOLD = 10_000;

	private record Attempts(int failures, Instant windowStart, Instant blockedUntil) {

		boolean expired(Instant now, Duration window) {
			return (blockedUntil == null || !now.isBefore(blockedUntil)) && !now.isBefore(windowStart.plus(window));
		}

	}

	private final Map<String, Attempts> byAccount = new ConcurrentHashMap<>();

	private final Map<String, Attempts> byClient = new ConcurrentHashMap<>();

	private final LoginThrottleProperties properties;

	private final Clock clock;

	LoginAttemptLimiter(LoginThrottleProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	/** How long the caller must still wait, if the account or the client is blocked. */
	public Optional<Duration> blockedFor(String email, String clientAddress) {
		Instant now = clock.instant();
		Duration account = remaining(byAccount.get(accountKey(email)), now);
		Duration client = remaining(byClient.get(clientAddress), now);
		Duration longest = account.compareTo(client) >= 0 ? account : client;
		return longest.isZero() ? Optional.empty() : Optional.of(longest);
	}

	public void recordFailure(String email, String clientAddress) {
		Instant now = clock.instant();
		byAccount.compute(accountKey(email), (key, current) -> fail(current, now, properties.maxFailuresPerAccount()));
		byClient.compute(clientAddress, (key, current) -> fail(current, now, properties.maxFailuresPerClient()));
		pruneIfLarge(now);
	}

	public void recordSuccess(String email) {
		byAccount.remove(accountKey(email));
	}

	/** Clears all counters (tests, or an operator unblocking after an incident). */
	public void reset() {
		byAccount.clear();
		byClient.clear();
	}

	private Attempts fail(Attempts current, Instant now, int limit) {
		Attempts base = current == null || current.expired(now, properties.window()) ? new Attempts(0, now, null)
				: current;
		int failures = base.failures() + 1;
		Instant blockedUntil = failures >= limit ? now.plus(properties.lockDuration()) : base.blockedUntil();
		return new Attempts(failures, base.windowStart(), blockedUntil);
	}

	private static Duration remaining(Attempts attempts, Instant now) {
		if (attempts == null || attempts.blockedUntil() == null || !now.isBefore(attempts.blockedUntil())) {
			return Duration.ZERO;
		}
		return Duration.between(now, attempts.blockedUntil());
	}

	private void pruneIfLarge(Instant now) {
		if (byAccount.size() + byClient.size() > PRUNE_THRESHOLD) {
			byAccount.values().removeIf(attempts -> attempts.expired(now, properties.window()));
			byClient.values().removeIf(attempts -> attempts.expired(now, properties.window()));
		}
	}

	private static String accountKey(String email) {
		return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
	}

}
