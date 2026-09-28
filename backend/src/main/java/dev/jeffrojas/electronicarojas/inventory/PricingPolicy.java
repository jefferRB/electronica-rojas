package dev.jeffrojas.electronicarojas.inventory;

import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.security.Role;

/**
 * Who may see what the company pays for its goods (single source, ENG-015). The unit cost is
 * internal: the API leaves it out (null) for everyone else, so hiding it in React is never the
 * only protection (SEC-003). Sale prices are not restricted: the counter quotes them to customers.
 */
public final class PricingPolicy {

	private PricingPolicy() {
	}

	/** ADMIN and BRANCH_MANAGER, the roles that operate inventory (BR-INV-A7, FS section 13). */
	public static boolean mayViewCost(CurrentUser user) {
		return user.role() == Role.ADMIN || user.role() == Role.BRANCH_MANAGER;
	}

}
