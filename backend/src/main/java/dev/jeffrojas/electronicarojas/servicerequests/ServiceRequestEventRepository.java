package dev.jeffrojas.electronicarojas.servicerequests;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ServiceRequestEventRepository extends JpaRepository<ServiceRequestEvent, Long> {

	@Query("select e from ServiceRequestEvent e left join fetch e.actor where e.request.id = :requestId order by e.occurredAt, e.id")
	List<ServiceRequestEvent> findTimeline(long requestId);

}
