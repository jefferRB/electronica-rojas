package dev.jeffrojas.electronicarojas.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Login throttling (SEC-006): failures counted per account (normalized email, whether or not it
 * exists) and per client address inside a sliding {@code window}; reaching a limit blocks further
 * attempts for {@code lockDuration}.
 */
@ConfigurationProperties("app.security.login")
public record LoginThrottleProperties(@DefaultValue("5") int maxFailuresPerAccount,
		@DefaultValue("20") int maxFailuresPerClient, @DefaultValue("PT15M") Duration window,
		@DefaultValue("PT15M") Duration lockDuration) {
}
