package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ServiceVisitRepository extends JpaRepository<ServiceVisit, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from ServiceVisit v join fetch v.request where v.id = :id")
	Optional<ServiceVisit> lockById(long id);

	Optional<ServiceVisit> findByOperationId(UUID operationId);

	@Query("""
			select v from ServiceVisit v join fetch v.technician
			where v.request.id = :requestId order by v.createdAt, v.id""")
	List<ServiceVisit> findForRequest(long requestId);

	@Query("""
			select v from ServiceVisit v
			where v.request.id = :requestId
			  and v.status in (dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus.PROPOSED,
			                   dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus.CONFIRMED,
			                   dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus.IN_PROGRESS)""")
	Optional<ServiceVisit> findActiveForRequest(long requestId);

	@Query("""
			select v from ServiceVisit v join fetch v.technician
			where v.request.id in :requestIds
			  and v.status in (dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus.PROPOSED,
			                   dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus.CONFIRMED,
			                   dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus.IN_PROGRESS)""")
	List<ServiceVisit> findActiveForRequests(Collection<Long> requestIds);

	@Query(value = "SELECT count(*) FROM (SELECT pg_advisory_xact_lock(" + ServiceRepositories.TECHNICIAN_LOCK_NAMESPACE
			+ ", CAST(:technicianId AS INTEGER))) AS technician_lock", nativeQuery = true)
	long lockTechnicianSchedule(long technicianId);

	/** CONFIRMED / IN_PROGRESS visits of a technician whose blocked range overlaps [from, until). */
	@Query("""
			select v from ServiceVisit v join fetch v.request r
			where v.technician.id = :technicianId
			  and v.status in (dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus.CONFIRMED,
			                   dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus.IN_PROGRESS)
			  and v.scheduledStart < :until and v.blockedUntil > :from
			  and (:excludeId is null or v.id <> :excludeId)
			order by v.scheduledStart""")
	List<ServiceVisit> findBlocking(long technicianId, Instant from, Instant until, Long excludeId);

	/**
	 * Agenda between two instants with the caller's scope: branch restriction (sentinel when
	 * empty) and, for technicians, only their own visits. Cancelled visits are left out.
	 */
	@Query("""
			select v from ServiceVisit v join fetch v.request r join fetch r.branch b left join fetch r.customer
			join fetch v.technician t
			where v.scheduledStart < :to and v.scheduledEnd > :from
			  and v.status <> dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus.CANCELLED
			  and (:restrictBranches = false or b.id in :branchIds)
			  and (:onlyTechnicianId is null or t.id = :onlyTechnicianId)
			  and (:branchId is null or b.id = :branchId)
			  and (:technicianId is null or t.id = :technicianId)
			order by v.scheduledStart, v.id""")
	List<ServiceVisit> agenda(boolean restrictBranches, Collection<Long> branchIds, Long onlyTechnicianId, Long branchId,
			Long technicianId, Instant from, Instant to);

	@Query("select v from ServiceVisit v join fetch v.request where v.repairOrder.id = :repairOrderId")
	Optional<ServiceVisit> findByRepairOrderId(long repairOrderId);

}
