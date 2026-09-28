package dev.jeffrojas.electronicarojas.shared.web;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 409: duplicates, stale versions and business-state conflicts (ER-FS-001 section 12).
 * {@code code} is a stable identifier the UI translates; {@code properties} add business data to
 * the ProblemDetail body (e.g. current balances). The message is technical English.
 */
public class ConflictException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String code;

	private final transient Map<String, Object> properties;

	public ConflictException(String code, String message) {
		this(code, message, Map.of());
	}

	public ConflictException(String code, String message, Map<String, Object> properties) {
		super(message);
		this.code = code;
		this.properties = Map.copyOf(properties);
	}

	public String code() {
		return code;
	}

	/** Properties for the ProblemDetail body, always including {@code code}. */
	public Map<String, Object> properties() {
		Map<String, Object> all = new LinkedHashMap<>(properties);
		all.put("code", code);
		return all;
	}

}
