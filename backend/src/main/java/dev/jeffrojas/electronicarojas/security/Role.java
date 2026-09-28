package dev.jeffrojas.electronicarojas.security;

/** Collaborator roles from ER-BR-001 section 3. One role per account. */
public enum Role {

	ADMIN,
	BRANCH_MANAGER,
	RECEPTIONIST,
	TECHNICIAN;

	/** Spring Security authority name, e.g. {@code ROLE_ADMIN}, matched by {@code hasRole('ADMIN')}. */
	public String authority() {
		return "ROLE_" + name();
	}

}
