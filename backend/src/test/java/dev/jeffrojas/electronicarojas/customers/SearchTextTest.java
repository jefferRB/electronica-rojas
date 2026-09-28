package dev.jeffrojas.electronicarojas.customers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SearchTextTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"Ana María Pérez        | ana maria perez",
			"  JOSÉ   Ñúñez  Güell  | jose nunez guell",
			"Ángela                 | angela" })
	void normalizesCaseAccentsAndSpaces(String raw, String expected) {
		assertThat(SearchText.normalize(raw)).isEqualTo(expected);
	}

	@Test
	void keepsAtMostThreeWords() {
		assertThat(SearchText.tokens(" Pérez  ana maría josé ")).containsExactly("perez", "ana", "maria");
		assertThat(SearchText.tokens("   ")).isEmpty();
		assertThat(SearchText.tokens(null)).isEmpty();
	}

	@Test
	void countsOnlySignificantCharacters() {
		assertThat(SearchText.significantLength(" a  n ")).isEqualTo(2);
		assertThat(SearchText.significantLength("Ána")).isEqualTo(3);
	}

	@Test
	void escapesLikeWildcards() {
		assertThat(SearchText.containsPattern("50%_a\\b")).isEqualTo("%50\\%\\_a\\\\b%");
	}

}
