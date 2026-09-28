package dev.jeffrojas.electronicarojas.audit;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

	/** Administrators: whole trail, optionally filtered. {@code to} is exclusive. */
	@Query("""
			select e from AuditEvent e
			where (:branchId is null or e.branchId = :branchId)
			  and (:entityType is null or e.entityType = :entityType)
			  and (:action is null or e.action = :action)
			  and (cast(:from as Instant) is null or e.occurredAt >= :from)
			  and (cast(:to as Instant) is null or e.occurredAt < :to)
			order by e.occurredAt desc, e.id desc
			""")
	Page<AuditEvent> search(Long branchId, String entityType, String action, Instant from, Instant to,
			Pageable pageable);

	/**
	 * Other roles: only events of ACTIVE branches assigned to the user, read from user_branches in
	 * the same query (native SQL keeps this module independent of the users/branches entities).
	 * The scope is applied in the database, before paging, never after.
	 */
	@Query(value = """
			SELECT e.* FROM audit_events e
			WHERE e.branch_id IN (SELECT ub.branch_id FROM user_branches ub
			                      JOIN branches b ON b.id = ub.branch_id
			                      WHERE ub.user_id = :userId AND b.active)
			  AND (CAST(:branchId AS BIGINT) IS NULL OR e.branch_id = :branchId)
			  AND (CAST(:entityType AS VARCHAR) IS NULL OR e.entity_type = :entityType)
			  AND (CAST(:action AS VARCHAR) IS NULL OR e.action = :action)
			  AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR e.occurred_at >= :from)
			  AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR e.occurred_at < :to)
			ORDER BY e.occurred_at DESC, e.id DESC
			""", countQuery = """
			SELECT count(*) FROM audit_events e
			WHERE e.branch_id IN (SELECT ub.branch_id FROM user_branches ub
			                      JOIN branches b ON b.id = ub.branch_id
			                      WHERE ub.user_id = :userId AND b.active)
			  AND (CAST(:branchId AS BIGINT) IS NULL OR e.branch_id = :branchId)
			  AND (CAST(:entityType AS VARCHAR) IS NULL OR e.entity_type = :entityType)
			  AND (CAST(:action AS VARCHAR) IS NULL OR e.action = :action)
			  AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR e.occurred_at >= :from)
			  AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR e.occurred_at < :to)
			""", nativeQuery = true)
	Page<AuditEvent> searchForAssignedBranches(long userId, Long branchId, String entityType, String action,
			Instant from, Instant to, Pageable pageable);

}
