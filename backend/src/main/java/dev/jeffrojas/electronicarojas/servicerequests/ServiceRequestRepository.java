package dev.jeffrojas.electronicarojas.servicerequests;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus;

interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from ServiceRequest r where r.id = :id")
	Optional<ServiceRequest> lockById(long id);

	Optional<ServiceRequest> findBySubmissionId(UUID submissionId);

	@Query("select r from ServiceRequest r join fetch r.branch where r.publicRef = :publicRef")
	Optional<ServiceRequest> findByPublicRef(UUID publicRef);

	@Query(value = "SELECT nextval('service_request_number_seq')", nativeQuery = true)
	long nextRequestNumber();

	@Query(value = "SELECT count(*) FROM (SELECT pg_advisory_xact_lock(" + ServiceRepositories.SUBMISSION_LOCK_NAMESPACE
			+ ", :key)) AS submission_lock", nativeQuery = true)
	long lockSubmission(int key);

	/**
	 * Inbox with the caller's scope in SQL: {@code restrictBranches} limits to {@code branchIds}
	 * (never empty: callers pass a sentinel). To-one fetch joins only, so paging stays in SQL.
	 */
	@Query(value = """
			select r from ServiceRequest r join fetch r.branch b left join fetch r.customer c
			where (:restrictBranches = false or b.id in :branchIds)
			  and (:branchId is null or b.id = :branchId)
			  and (:status is null or r.status = :status)
			  and (:customerId is null or c.id = :customerId)
			  and (:pattern is null or lower(r.requestCode) like :pattern escape '\\'
			       or lower(r.contactName) like :pattern escape '\\' or lower(r.deviceType) like :pattern escape '\\'
			       or lower(r.canton) like :pattern escape '\\')
			order by r.createdAt desc, r.id desc
			""", countQuery = """
			select count(r) from ServiceRequest r join r.branch b left join r.customer c
			where (:restrictBranches = false or b.id in :branchIds)
			  and (:branchId is null or b.id = :branchId)
			  and (:status is null or r.status = :status)
			  and (:customerId is null or c.id = :customerId)
			  and (:pattern is null or lower(r.requestCode) like :pattern escape '\\'
			       or lower(r.contactName) like :pattern escape '\\' or lower(r.deviceType) like :pattern escape '\\'
			       or lower(r.canton) like :pattern escape '\\')
			""")
	Page<ServiceRequest> search(boolean restrictBranches, Collection<Long> branchIds, Long branchId, RequestStatus status,
			Long customerId, String pattern, Pageable pageable);

}
