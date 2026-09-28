package dev.jeffrojas.electronicarojas.inventory;

import java.util.Locale;

/** Builds a case-insensitive "contains" LIKE pattern, escaping the user's own % and _ characters. */
final class SearchPattern {

	private SearchPattern() {
	}

	static String contains(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String escaped = text.strip()
			.toLowerCase(Locale.ROOT)
			.replace("\\", "\\\\")
			.replace("%", "\\%")
			.replace("_", "\\_");
		return "%" + escaped + "%";
	}

}
