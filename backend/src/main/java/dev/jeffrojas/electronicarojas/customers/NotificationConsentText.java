package dev.jeffrojas.electronicarojas.customers;

/**
 * Version of the consent text customers accept (BR-CUS-006). The SPA shows the text of this
 * version (frontend/src/shared/i18n/consent.ts); changing the wording means a new version in both
 * places, so every stored consent keeps pointing to the exact text that was accepted.
 */
public final class NotificationConsentText {

	public static final String CURRENT_VERSION = "AVISOS-2026-09";

	private NotificationConsentText() {
	}

}
