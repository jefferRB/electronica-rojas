package dev.jeffrojas.electronicarojas.notifications;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.Status;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SubjectType;

interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

	boolean existsByDedupeKey(String dedupeKey);

	List<OutboxMessage> findBySubjectTypeAndSubjectIdInOrderByCreatedAtAscIdAsc(SubjectType subjectType,
			Collection<Long> subjectIds);

	/** Admin list with the caller's branch scope in SQL (sentinel list when restricted and empty). */
	@Query(value = """
			select m from OutboxMessage m
			where (:restrictBranches = false or m.branchId in :branchIds)
			  and (:branchId is null or m.branchId = :branchId)
			  and (:status is null or m.status = :status)
			  and (cast(:from as Instant) is null or m.createdAt >= :from)
			order by m.createdAt desc, m.id desc""", countQuery = """
			select count(m) from OutboxMessage m
			where (:restrictBranches = false or m.branchId in :branchIds)
			  and (:branchId is null or m.branchId = :branchId)
			  and (:status is null or m.status = :status)
			  and (cast(:from as Instant) is null or m.createdAt >= :from)""")
	Page<OutboxMessage> search(boolean restrictBranches, Collection<Long> branchIds, Long branchId, Status status,
			Instant from, Pageable pageable);

}
