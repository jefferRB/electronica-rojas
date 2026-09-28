package dev.jeffrojas.electronicarojas.users;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import dev.jeffrojas.electronicarojas.security.Role;

interface AppUserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByRoleAndActiveTrue(Role role);

	/** Active technicians explicitly assigned to a branch (BR-BRH-002). */
	@Query("""
			select u from AppUser u join u.branches b
			where u.role = dev.jeffrojas.electronicarojas.security.Role.TECHNICIAN and u.active = true and b.id = :branchId
			order by u.fullName, u.id
			""")
	List<AppUser> findActiveTechniciansOf(long branchId);

	/**
	 * SELECT ... FOR UPDATE on the active accounts of a role. Two administrators demoting each
	 * other at the same time are serialized, so the "last active admin" check cannot be raced.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from AppUser u where u.role = :role and u.active = true order by u.id")
	List<AppUser> lockActiveByRole(Role role);

}
