package dev.jeffrojas.electronicarojas.users;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.security.Role;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;

/**
 * Creates the first administrator at startup when configured. Idempotent: it does nothing if
 * any active administrator already exists, and it never modifies an existing account.
 * The password is never logged.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final AdminBootstrapProperties properties;

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	private final TransactionTemplate transaction;

	private final AuditService audit;

	AdminBootstrap(AdminBootstrapProperties properties, AppUserRepository users, PasswordEncoder passwordEncoder,
			Clock clock, TransactionTemplate transaction, AuditService audit) {
		this.properties = properties;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.transaction = transaction;
		this.audit = audit;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.isConfigured()) {
			log.debug("Admin bootstrap not configured; skipping");
			return;
		}
		try {
			transaction.executeWithoutResult(status -> createIfMissing());
		}
		catch (DataIntegrityViolationException ex) {
			// Another instance created the same account concurrently: the goal is already met.
			log.info("Admin bootstrap skipped: account created concurrently");
		}
	}

	private void createIfMissing() {
		if (users.existsByRoleAndActiveTrue(Role.ADMIN)) {
			log.info("Admin bootstrap skipped: an active administrator already exists");
			return;
		}
		String email = AppUser.normalizeEmail(properties.email());
		if (users.existsByEmail(email)) {
			log.warn("Admin bootstrap skipped: the configured email belongs to an existing account, which is not modified");
			return;
		}
		// Fails startup on a weak password instead of creating a weak admin account (fail closed).
		try {
			PasswordPolicy.validate("BOOTSTRAP_ADMIN_PASSWORD", properties.password());
		}
		catch (InvalidRequestException ex) {
			throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD " + ex.getMessage(), ex);
		}
		AppUser admin = new AppUser(email, properties.fullName(), passwordEncoder.encode(properties.password()),
				Role.ADMIN, clock.instant());
		users.save(admin);
		audit.recordSystem(AuditEntry.of(AuditAction.USER_CREATED, admin.getId())
			.detail("userName", admin.getFullName())
			.detail("role", Role.ADMIN)
			.detail("bootstrap", true)
			.summary("Initial administrator created by bootstrap"));
		log.info("Admin bootstrap created the initial administrator (user id {})", admin.getId());
	}

}
