package dev.jeffrojas.electronicarojas.users;

import java.nio.charset.StandardCharsets;

import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;

/**
 * Rules for passwords chosen by an administrator or the bootstrap: at least 12 characters and at
 * most 72 bytes in UTF-8 (BCrypt ignores anything longer, so it is rejected instead of silently cut).
 * No composition rules (upper case, digits, symbols): SEC-001 does not require them and length is
 * the stronger control (NIST SP 800-63B). Exposed to the UI via GET /api/v1/auth/password-policy.
 */
final class PasswordPolicy {

	static final int MIN_LENGTH = 12;

	static final int MAX_BYTES = 72;

	private PasswordPolicy() {
	}

	static void validate(String field, String rawPassword) {
		if (rawPassword == null || rawPassword.isBlank()) {
			throw new InvalidRequestException(field, "PASSWORD_REQUIRED", "must not be blank");
		}
		if (rawPassword.codePointCount(0, rawPassword.length()) < MIN_LENGTH) {
			throw new InvalidRequestException(field, "PASSWORD_TOO_SHORT",
					"must have at least " + MIN_LENGTH + " characters");
		}
		if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			throw new InvalidRequestException(field, "PASSWORD_TOO_LONG", "must not exceed " + MAX_BYTES + " bytes");
		}
	}

	/** What the UI shows next to password fields: exactly the rules enforced above. */
	record Description(int minLength, int maxBytes) {
	}

	static Description describe() {
		return new Description(MIN_LENGTH, MAX_BYTES);
	}

}
