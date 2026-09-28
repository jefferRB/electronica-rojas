package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.LocalDate;

import dev.jeffrojas.electronicarojas.servicerequests.PublicPortalSettings.Rules;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.PreferredWindow;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.Province;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;

/**
 * What the public form accepts under the current portal settings (BR-SRV-009). The preferred date
 * and window stay a wish: passing these checks never books anything (BR-SRV-001). Pure Java; dates
 * are local days in Costa Rica, computed by the caller.
 */
final class PortalRules {

	private PortalRules() {
	}

	static LocalDate earliestDate(Rules rules, LocalDate today) {
		return today.plusDays(rules.minNoticeDays());
	}

	static LocalDate latestDate(Rules rules, LocalDate today) {
		return today.plusDays(rules.maxDaysAhead());
	}

	/** Throws a 400 with a stable code for the first rule the submission breaks. */
	static void check(Rules rules, LocalDate today, LocalDate preferredDate, PreferredWindow window, Province province) {
		if (!rules.servedProvinces().contains(province)) {
			throw new InvalidRequestException("province", "PROVINCE_NOT_SERVED", "the business does not serve this province");
		}
		if (window != null && window != PreferredWindow.ANY && !rules.allowPreferredWindow()) {
			throw new InvalidRequestException("preferredWindow", "WINDOW_NOT_ALLOWED",
					"the business does not take preferred windows");
		}
		if (preferredDate == null) {
			return;
		}
		if (!rules.allowPreferredDate()) {
			throw new InvalidRequestException("preferredDate", "DATE_NOT_ALLOWED",
					"the business does not take preferred dates");
		}
		if (preferredDate.isBefore(today)) {
			throw new InvalidRequestException("preferredDate", "DATE_IN_PAST", "must not be in the past");
		}
		if (preferredDate.isBefore(earliestDate(rules, today))) {
			throw new InvalidRequestException("preferredDate", "DATE_TOO_SOON",
					"must be at least " + rules.minNoticeDays() + " day(s) ahead");
		}
		if (preferredDate.isAfter(latestDate(rules, today))) {
			throw new InvalidRequestException("preferredDate", "DATE_TOO_FAR",
					"must be within " + rules.maxDaysAhead() + " days");
		}
		if (!rules.serviceDays().contains(preferredDate.getDayOfWeek().getValue())) {
			throw new InvalidRequestException("preferredDate", "DAY_NOT_SERVED", "the business does not visit on that day");
		}
	}

}
