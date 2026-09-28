package dev.jeffrojas.electronicarojas.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.security.Role;

/** Pure unit tests (Mockito, no Spring, no Docker) for the bootstrap decisions. */
class AdminBootstrapTest {

	private final AppUserRepository users = mock(AppUserRepository.class);

	private final PasswordEncoder encoder = mock(PasswordEncoder.class);

	private final Clock clock = Clock.fixed(Instant.parse("2026-09-25T18:00:00Z"), ZoneOffset.UTC);

	private final TransactionTemplate transaction = new TransactionTemplate(mock(PlatformTransactionManager.class));

	private AdminBootstrap bootstrap(String email, String password) {
		return new AdminBootstrap(new AdminBootstrapProperties(email, password, "Administrador"), users, encoder, clock,
				transaction, mock(AuditService.class));
	}

	@Test
	void doesNothingWhenNotConfigured() {
		bootstrap("", "").run(null);

		verifyNoInteractions(users, encoder);
	}

	@Test
	void createsNormalizedAdminWhenNoneExists() {
		given(encoder.encode("long-enough-password")).willReturn("{bcrypt}hash");

		bootstrap(" Boss@Electronica-Rojas.test ", "long-enough-password").run(null);

		ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
		verify(users).save(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("boss@electronica-rojas.test");
		assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
		assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}hash");
	}

	@Test
	void skipsWhenAnActiveAdministratorExists() {
		given(users.existsByRoleAndActiveTrue(Role.ADMIN)).willReturn(true);

		bootstrap("boss@electronica-rojas.test", "long-enough-password").run(null);

		verify(users, never()).save(any());
	}

	@Test
	void neverTakesOverAnExistingAccount() {
		given(users.existsByEmail("boss@electronica-rojas.test")).willReturn(true);

		bootstrap("boss@electronica-rojas.test", "long-enough-password").run(null);

		verify(users, never()).save(any());
	}

	@Test
	void refusesToStartWithAWeakPassword() {
		assertThatThrownBy(() -> bootstrap("boss@electronica-rojas.test", "short").run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("BOOTSTRAP_ADMIN_PASSWORD must have at least 12 characters");
		verify(users, never()).save(any());
	}

	@Test
	void propertiesNeverPrintThePassword() {
		assertThat(new AdminBootstrapProperties("a@b.test", "secret-value-123", "X").toString())
			.doesNotContain("secret-value-123");
	}

}
