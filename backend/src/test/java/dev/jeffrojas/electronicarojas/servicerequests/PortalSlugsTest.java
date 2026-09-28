package dev.jeffrojas.electronicarojas.servicerequests;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** BR-SRV-009: format and reserved words of the portal's public address. */
class PortalSlugsTest {

	@ParameterizedTest
	@ValueSource(strings = { "servicio-a-domicilio", "electronica-perez", "abc", "taller24", "a1-b2-c3" })
	void acceptsLowerCaseLettersDigitsAndSingleHyphens(String slug) {
		assertThat(PortalSlugs.problem(slug)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "ab", "-inicio", "fin-", "dos--guiones", "con espacio", "acentuación", "punto.com",
			"barra/x", "MAYUS" })
	void rejectsAnythingElse(String slug) {
		assertThat(PortalSlugs.problem(slug)).contains("SLUG_INVALID");
	}

	@Test
	void rejectsTooLongSlugs() {
		assertThat(PortalSlugs.problem("a".repeat(61))).contains("SLUG_INVALID");
		assertThat(PortalSlugs.problem("a".repeat(60))).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "admin", "api", "login", "portal", "estado" })
	void rejectsReservedWords(String slug) {
		assertThat(PortalSlugs.problem(slug)).contains("SLUG_RESERVED");
	}

	@Test
	void normalizingOnlyTrimsAndLowerCases() {
		assertThat(PortalSlugs.normalize("  Electronica-Perez ")).isEqualTo("electronica-perez");
		// Nothing else is rewritten silently: a space stays and makes the slug invalid.
		assertThat(PortalSlugs.problem(PortalSlugs.normalize("Mi Portal"))).contains("SLUG_INVALID");
	}

}
