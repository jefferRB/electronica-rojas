package dev.jeffrojas.electronicarojas.users;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;

class PasswordPolicyTest {

	@Test
	void acceptsTwelveCharacters() {
		assertThatCode(() -> PasswordPolicy.validate("password", "abcdefghijkl")).doesNotThrowAnyException();
	}

	@Test
	void rejectsBlankOrShortPasswords() {
		assertThatThrownBy(() -> PasswordPolicy.validate("password", "   ")).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> PasswordPolicy.validate("password", "abcdefghijk"))
			.isInstanceOf(InvalidRequestException.class);
	}

	/** BCrypt ignores bytes after 72; accepting them would silently weaken the password. */
	@Test
	void rejectsPasswordsLongerThanBcryptCanHash() {
		assertThatCode(() -> PasswordPolicy.validate("password", "a".repeat(72))).doesNotThrowAnyException();
		assertThatThrownBy(() -> PasswordPolicy.validate("password", "a".repeat(73)))
			.isInstanceOf(InvalidRequestException.class);
		// 25 characters but 75 bytes in UTF-8
		assertThatThrownBy(() -> PasswordPolicy.validate("password", "ñ".repeat(25) + "€".repeat(8)))
			.isInstanceOf(InvalidRequestException.class);
	}

}
