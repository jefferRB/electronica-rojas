package dev.jeffrojas.electronicarojas.customers;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Name normalization shared by storage ({@code customers.search_name}) and search terms, so that
 * "PÉREZ", "perez" and "Pérez  " compare equal: lower case, no diacritics (ñ → n), single spaces.
 */
public final class SearchText {

	/** Only the first words of a name search are used; more would add nothing but cost. */
	static final int MAX_TOKENS = 3;

	private static final Pattern MARKS = Pattern.compile("\\p{M}+");

	private static final Pattern SPACES = Pattern.compile("\\s+");

	private SearchText() {
	}

	/** "  María  José Pérez " → "maria jose perez". Null stays null. */
	public static String normalize(String text) {
		if (text == null) {
			return null;
		}
		String withoutMarks = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
		return SPACES.matcher(withoutMarks.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
	}

	/** Normalized words of a search term, at most {@link #MAX_TOKENS}; empty for blank input. */
	static List<String> tokens(String term) {
		String normalized = normalize(term);
		if (normalized == null || normalized.isEmpty()) {
			return List.of();
		}
		return Arrays.stream(normalized.split(" ")).limit(MAX_TOKENS).toList();
	}

	/** Letters and digits typed, ignoring spaces: the "at least 3 characters" rule. */
	static int significantLength(String term) {
		String normalized = normalize(term);
		return normalized == null ? 0 : normalized.replace(" ", "").length();
	}

	/** SQL LIKE pattern "contains", with LIKE wildcards escaped (ESCAPE '\'). */
	static String containsPattern(String token) {
		return "%" + token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
	}

}
