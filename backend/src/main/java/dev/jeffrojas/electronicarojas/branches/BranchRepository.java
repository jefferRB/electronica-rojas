package dev.jeffrojas.electronicarojas.branches;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BranchRepository extends JpaRepository<Branch, Long> {

	boolean existsByCode(String code);

	List<Branch> findAllByOrderByCodeAsc();

	List<Branch> findByActiveTrueOrderByCodeAsc();

	List<Branch> findByIdIn(Collection<Long> ids);

	/*
	 * The two queries below read the user_branches join table directly (native SQL) so the
	 * branches module does not depend on the users module's AppUser entity.
	 */

	@Query(value = """
			SELECT b.* FROM branches b
			JOIN user_branches ub ON ub.branch_id = b.id
			WHERE ub.user_id = :userId AND b.active
			ORDER BY b.code
			""", nativeQuery = true)
	List<Branch> findActiveAssignedTo(long userId);

	@Query(value = """
			SELECT b.* FROM branches b
			JOIN user_branches ub ON ub.branch_id = b.id
			WHERE ub.user_id = :userId AND b.id = :branchId AND b.active
			""", nativeQuery = true)
	Optional<Branch> findActiveAssigned(long userId, long branchId);

}
