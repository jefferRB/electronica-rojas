package dev.jeffrojas.electronicarojas.users;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only access to technicians for other modules (repairs): who may be assigned to work at a
 * branch. Keeps AppUserRepository private to the users module.
 */
@Service
@Transactional(readOnly = true)
public class TechnicianDirectory {

	private final AppUserRepository users;

	TechnicianDirectory(AppUserRepository users) {
		this.users = users;
	}

	/** Active TECHNICIAN accounts assigned to the branch. */
	public List<AppUser> activeTechniciansOf(long branchId) {
		return users.findActiveTechniciansOf(branchId);
	}

	/** The technician, only if active, a TECHNICIAN and assigned to that branch. */
	public Optional<AppUser> eligibleTechnician(long userId, long branchId) {
		return users.findActiveTechniciansOf(branchId).stream().filter(user -> user.getId() == userId).findFirst();
	}

}
