package dev.jeffrojas.electronicarojas.repairs;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface RepairStatusChangeRepository extends JpaRepository<RepairStatusChange, Long> {

	@Query("select h from RepairStatusChange h join fetch h.actor where h.order.id = :orderId order by h.changedAt, h.id")
	List<RepairStatusChange> findTimeline(long orderId);

}
