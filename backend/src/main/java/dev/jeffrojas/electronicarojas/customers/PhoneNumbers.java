package dev.jeffrojas.electronicarojas.customers;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Phone normalization to E.164 (BR-CUS-002: normalize before comparing).
 * <ul>
 * <li>Costa Rica: 8 national digits, with or without +506 / 506 / 00506 and with spaces, dashes,
 * dots or parentheses: "8888-7777", "+506 8888 7777", "(506) 2222-3333".</li>
 * <li>Other countries: accepted in international form ("+1 305 555 0100", "001 305...").</li>
 * </ul>
 * Only the number shape is checked; no prefix list is enforced, so valid numbers are not rejected
 * because a table is out of date.
 */
public final class PhoneNumbers {

	private static final Pattern SEPARATORS = Pattern.compile("[\\s().-]");

	private static final Pattern DIGITS = Pattern.compile("\\d+");

	private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{7,14}");

	private static final String COSTA_RICA = "506";

	private PhoneNumbers() {
	}

	/** The E.164 form, or empty if the input cannot be a phone number. */
	public static Optional<String> normalize(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		String compact = SEPARATORS.matcher(raw.strip()).replaceAll("");
		boolean international = compact.startsWith("+");
		String digits = international ? compact.substring(1) : compact;
		if (!DIGITS.matcher(digits).matches()) {
			return Optional.empty();
		}
		if (!international && digits.startsWith("00")) {
			digits = digits.substring(2);
			international = true;
		}
		if (!international) {
			if (digits.length() == 8) {
				digits = COSTA_RICA + digits;
			}
			else if (!(digits.length() == 11 && digits.startsWith(COSTA_RICA))) {
				return Optional.empty();
			}
		}
		String e164 = "+" + digits;
		if (!E164.matcher(e164).matches()) {
			return Optional.empty();
		}
		if (digits.startsWith(COSTA_RICA) && digits.length() != 11) {
			return Optional.empty();
		}
		return Optional.of(e164);
	}

	/** Digits only (for "contains" search), or null when there are fewer than 3 of them. */
	static String searchDigits(String text) {
		if (text == null) {
			return null;
		}
		String digits = text.replaceAll("\\D", "");
		return digits.length() >= 3 ? digits : null;
	}

}
