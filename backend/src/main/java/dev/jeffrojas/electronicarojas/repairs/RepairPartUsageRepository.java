package dev.jeffrojas.electronicarojas.repairs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RepairPartUsageRepository extends JpaRepository<RepairPartUsage, Long> {

	@Query("select u from RepairPartUsage u join fetch u.recordedBy where u.operationId = :operationId")
	Optional<RepairPartUsage> findByOperationId(UUID operationId);

	/**
	 * Row lock on one line of the order, taken after the order's own lock (order, then line, then
	 * stock row): returns of the same line are serialized and cannot exceed what was consumed.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from RepairPartUsage u where u.id = :id and u.order.id = :orderId")
	Optional<RepairPartUsage> lockByIdAndOrderId(long id, long orderId);

	@Query("""
			select u from RepairPartUsage u join fetch u.product join fetch u.branch join fetch u.recordedBy
			where u.order.id = :orderId order by u.recordedAt, u.id""")
	List<RepairPartUsage> findForOrder(long orderId);

}
