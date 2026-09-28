package dev.jeffrojas.electronicarojas.customers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** BR-CUS-002: every accepted spelling of the same number normalizes to one E.164 value. */
class PhoneNumbersTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"8888-7777        | +50688887777",
			"8888 7777        | +50688887777",
			"88887777         | +50688887777",
			"+506 8888 7777   | +50688887777",
			"(506) 2222-3333  | +50622223333",
			"506 2222.3333    | +50622223333",
			"00506 8888 7777  | +50688887777",
			"+1 305 555 0100  | +13055550100",
			"001 305 555 0100 | +13055550100",
			"+44 20 7946 0958 | +442079460958" })
	void normalizesCostaRicanAndInternationalNumbers(String raw, String expected) {
		assertThat(PhoneNumbers.normalize(raw)).contains(expected);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "1234567", "888877776", "+506 8888 777", "+506 8888 77777", "8888-777a",
			"+0 305 555 0100", "abc", "+1234567" })
	void rejectsValuesThatCannotBeAPhone(String raw) {
		assertThat(PhoneNumbers.normalize(raw)).isEmpty();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "NULL", value = {
			"8888-7777 | 88887777",
			"tel 2222  | 2222",
			"123       | 123",
			"12        | NULL",
			"Ana       | NULL" })
	void searchDigitsNeedAtLeastThree(String text, String expected) {
		assertThat(PhoneNumbers.searchDigits(text)).isEqualTo(expected);
	}

}
