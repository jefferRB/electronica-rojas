package dev.jeffrojas.electronicarojas.repairs;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RepairOrderRepository extends JpaRepository<RepairOrder, Long> {

	/**
	 * SELECT ... FOR UPDATE on one order: every workflow change (status, quote decision, technician,
	 * diagnosis) serializes on this row, so two people delivering the same appliance at once cannot
	 * both succeed.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from RepairOrder o where o.id = :id")
	Optional<RepairOrder> lockById(long id);

	Optional<RepairOrder> findByIntakeOperationId(UUID intakeOperationId);

	/**
	 * Transaction-scoped advisory lock on a reception's operationId: concurrent duplicates of the
	 * same submit wait for the first one to commit and then replay it, instead of racing on the
	 * customer and order inserts. Released automatically at commit or rollback.
	 */
	@Query(value = "SELECT count(*) FROM (SELECT pg_advisory_xact_lock(:key)) AS intake_lock", nativeQuery = true)
	long lockIntake(long key);

	@Query(value = "SELECT nextval('repair_order_number_seq')", nativeQuery = true)
	long nextOrderNumber();

	/**
	 * Order list with the caller's scope applied in SQL: {@code restrictBranches} limits to
	 * {@code branchIds} (never empty: callers pass a sentinel), {@code onlyTechnicianId} limits a
	 * technician to their own orders. Fetch joins are to-one only, so paging stays in the database.
	 */
	@Query(value = """
			select o from RepairOrder o
			join fetch o.customer c join fetch o.branch b left join fetch o.assignedTechnician t
			where (:restrictBranches = false or b.id in :branchIds)
			  and (:onlyTechnicianId is null or t.id = :onlyTechnicianId)
			  and (:branchId is null or b.id = :branchId)
			  and (:status is null or o.status = :status)
			  and (:technicianId is null or t.id = :technicianId)
			  and (:customerId is null or c.id = :customerId)
			  and (:pattern is null or lower(o.orderCode) like :pattern escape '\\' or lower(c.fullName) like :pattern escape '\\'
			       or lower(o.brand) like :pattern escape '\\' or lower(coalesce(o.serialNumber, '')) like :pattern escape '\\')
			  and (cast(:from as Instant) is null or o.receivedAt >= :from)
			  and (cast(:to as Instant) is null or o.receivedAt < :to)
			order by o.receivedAt desc, o.id desc
			""", countQuery = """
			select count(o) from RepairOrder o join o.customer c join o.branch b left join o.assignedTechnician t
			where (:restrictBranches = false or b.id in :branchIds)
			  and (:onlyTechnicianId is null or t.id = :onlyTechnicianId)
			  and (:branchId is null or b.id = :branchId)
			  and (:status is null or o.status = :status)
			  and (:technicianId is null or t.id = :technicianId)
			  and (:customerId is null or c.id = :customerId)
			  and (:pattern is null or lower(o.orderCode) like :pattern escape '\\' or lower(c.fullName) like :pattern escape '\\'
			       or lower(o.brand) like :pattern escape '\\' or lower(coalesce(o.serialNumber, '')) like :pattern escape '\\')
			  and (cast(:from as Instant) is null or o.receivedAt >= :from)
			  and (cast(:to as Instant) is null or o.receivedAt < :to)
			""")
	Page<RepairOrder> search(boolean restrictBranches, Collection<Long> branchIds, Long onlyTechnicianId, Long branchId,
			RepairStatus status, Long technicianId, Long customerId, String pattern, Instant from, Instant to,
			Pageable pageable);

}
