package dev.jeffrojas.electronicarojas.repairs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface RepairPartReturnRepository extends JpaRepository<RepairPartReturn, Long> {

	@Query("select r from RepairPartReturn r join fetch r.usage join fetch r.recordedBy where r.operationId = :operationId")
	Optional<RepairPartReturn> findByOperationId(UUID operationId);

	@Query("""
			select r from RepairPartReturn r join fetch r.recordedBy
			where r.usage.order.id = :orderId order by r.recordedAt, r.id""")
	List<RepairPartReturn> findForOrder(long orderId);

}
