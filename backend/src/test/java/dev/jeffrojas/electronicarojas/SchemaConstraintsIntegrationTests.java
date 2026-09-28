package dev.jeffrojas.electronicarojas;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import dev.jeffrojas.electronicarojas.security.Role;

/**
 * DATA-001: PostgreSQL itself enforces the identity invariants, even for writes that bypass
 * the services (scripts, future modules, bugs).
 */
class SchemaConstraintsIntegrationTests extends IntegrationTestSupport {

	@Test
	void branchCodesMustBeNormalizedAndUnique() {
		createBranch("SJ-01");

		assertThatThrownBy(() -> createBranch("sj-02")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> createBranch("SJ-01")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void emailsMustBeStoredNormalized() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO app_users (email, full_name, password_hash, role, created_at, updated_at)
				VALUES ('Upper@Electronica-Rojas.test', 'X', 'h', 'ADMIN', now(), now())
				""")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void unknownRolesAreRejected() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO app_users (email, full_name, password_hash, role, created_at, updated_at)
				VALUES ('root@electronica-rojas.test', 'X', 'h', 'SUPERUSER', now(), now())
				""")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void anAssignmentExistsAtMostOnceAndOnlyForExistingRows() {
		long branch = createBranch("SJ-01");
		long user = createUser("tech@electronica-rojas.test", Role.TECHNICIAN, branch);

		assertThatThrownBy(() -> jdbc.update("INSERT INTO user_branches (user_id, branch_id) VALUES (?, ?)", user, branch))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("INSERT INTO user_branches (user_id, branch_id) VALUES (?, ?)", user, -1L))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void identityKeysCannotBeSetManually() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO branches (id, code, name, created_at, updated_at) VALUES (42, 'MAN-01', 'X', now(), now())
				""")).isInstanceOf(DataAccessException.class);
	}

}
