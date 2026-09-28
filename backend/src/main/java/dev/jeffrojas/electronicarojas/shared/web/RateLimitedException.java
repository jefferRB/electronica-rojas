package dev.jeffrojas.electronicarojas.shared.web;

import java.io.Serial;
import java.time.Duration;

/** 429: too many requests from one client; {@code retryAfter} becomes the Retry-After header. */
public class RateLimitedException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	private final transient Duration retryAfter;

	public RateLimitedException(Duration retryAfter) {
		super("Too many requests. Try again later.");
		this.retryAfter = retryAfter;
	}

	public Duration retryAfter() {
		return retryAfter;
	}

}
