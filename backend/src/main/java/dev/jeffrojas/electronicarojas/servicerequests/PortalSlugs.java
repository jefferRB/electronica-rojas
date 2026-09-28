package dev.jeffrojas.electronicarojas.servicerequests;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rules of the public portal's address (BR-SRV-009): lower-case letters, digits and single hyphens,
 * 3-60 characters, not a reserved word. Pure Java, unit-tested.
 */
final class PortalSlugs {

	static final int MIN_LENGTH = 3;

	static final int MAX_LENGTH = 60;

	private static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

	/** Words that name parts of the system; a customer link must never look like one. */
	static final Set<String> RESERVED = Set.of("admin", "administracion", "api", "app", "assets", "auth",
			"config", "configuracion", "dashboard", "estado", "inicio", "login", "logout", "new", "nuevo", "null",
			"portal", "public", "publico", "settings", "static", "undefined", "www");

	private PortalSlugs() {
	}

	/** Why a slug is refused (an error code), or empty when it is valid. Expects {@link #normalize}d input. */
	static Optional<String> problem(String slug) {
		if (slug.length() < MIN_LENGTH || slug.length() > MAX_LENGTH || !FORMAT.matcher(slug).matches()) {
			return Optional.of("SLUG_INVALID");
		}
		if (RESERVED.contains(slug)) {
			return Optional.of("SLUG_RESERVED");
		}
		return Optional.empty();
	}

	/** What the administrator typed, trimmed and lower-cased; nothing else is changed silently. */
	static String normalize(String input) {
		return input == null ? "" : input.strip().toLowerCase(Locale.ROOT);
	}

}
