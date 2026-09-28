package dev.jeffrojas.electronicarojas.servicerequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jeffrojas.electronicarojas.servicerequests.PublicPortalSettings.Rules;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.PreferredWindow;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.Province;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;

/** BR-SRV-009: what the public form accepts under the portal settings. */
class PortalRulesTest {

	/** A Wednesday. */
	private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

	/** Two days of notice, up to 14 days ahead, Monday to Friday, San José and Heredia, windows allowed. */
	private static Rules rules(boolean allowDate, boolean allowWindow) {
		return new Rules(true, allowDate, allowWindow, 2, 14, List.of(1, 2, 3, 4, 5),
				List.of(Province.SAN_JOSE, Province.HEREDIA), List.of(), null, null);
	}

	private static String codeOf(Runnable check) {
		try {
			check.run();
			return null;
		}
		catch (InvalidRequestException ex) {
			return ex.code();
		}
	}

	@Test
	void acceptsAPreferenceInsideEveryRule() {
		assertThatCode(() -> PortalRules.check(rules(true, true), TODAY, LocalDate.of(2026, 10, 2),
				PreferredWindow.MORNING, Province.SAN_JOSE)).doesNotThrowAnyException();
		// No date at all is always fine: the preference is optional.
		assertThatCode(() -> PortalRules.check(rules(false, false), TODAY, null, PreferredWindow.ANY, Province.HEREDIA))
			.doesNotThrowAnyException();
	}

	@Test
	void datesComputedInLocalDays() {
		assertThat(PortalRules.earliestDate(rules(true, true), TODAY)).isEqualTo(LocalDate.of(2026, 10, 2));
		assertThat(PortalRules.latestDate(rules(true, true), TODAY)).isEqualTo(LocalDate.of(2026, 10, 14));
	}

	@Test
	void eachBrokenRuleHasItsOwnCode() {
		Rules rules = rules(true, true);
		assertThat(codeOf(() -> PortalRules.check(rules, TODAY, null, PreferredWindow.ANY, Province.LIMON)))
			.isEqualTo("PROVINCE_NOT_SERVED");
		assertThat(codeOf(() -> PortalRules.check(rules, TODAY, TODAY.minusDays(1), null, Province.SAN_JOSE)))
			.isEqualTo("DATE_IN_PAST");
		assertThat(codeOf(() -> PortalRules.check(rules, TODAY, TODAY.plusDays(1), null, Province.SAN_JOSE)))
			.isEqualTo("DATE_TOO_SOON");
		assertThat(codeOf(() -> PortalRules.check(rules, TODAY, TODAY.plusDays(15), null, Province.SAN_JOSE)))
			.isEqualTo("DATE_TOO_FAR");
		// 2026-10-03 is a Saturday.
		assertThat(codeOf(() -> PortalRules.check(rules, TODAY, LocalDate.of(2026, 10, 3), null, Province.SAN_JOSE)))
			.isEqualTo("DAY_NOT_SERVED");
		assertThat(codeOf(() -> PortalRules.check(rules(false, true), TODAY, LocalDate.of(2026, 10, 2), null,
				Province.SAN_JOSE))).isEqualTo("DATE_NOT_ALLOWED");
		assertThat(codeOf(() -> PortalRules.check(rules(true, false), TODAY, null, PreferredWindow.AFTERNOON,
				Province.SAN_JOSE))).isEqualTo("WINDOW_NOT_ALLOWED");
	}

	@Test
	void anyWindowIsNotAPreference() {
		assertThatCode(() -> PortalRules.check(rules(true, false), TODAY, null, PreferredWindow.ANY, Province.SAN_JOSE))
			.doesNotThrowAnyException();
		assertThatThrownBy(() -> PortalRules.check(rules(true, false), TODAY, null, PreferredWindow.MORNING,
				Province.SAN_JOSE)).isInstanceOf(InvalidRequestException.class);
	}

}
