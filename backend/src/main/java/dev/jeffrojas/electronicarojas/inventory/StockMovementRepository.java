package dev.jeffrojas.electronicarojas.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

	/** A standalone operation (receipt, issue, adjustment) has exactly one movement. */
	Optional<StockMovement> findByOperationIdAndTransferIsNull(UUID operationId);

	@Query("select m from StockMovement m join fetch m.branch join fetch m.actor where m.transfer.id = :transferId order by m.id")
	List<StockMovement> findByTransfer(long transferId);

	/**
	 * Branch history, newest first. Fetch joins on to-one relations only, so the database still
	 * does the pagination (no in-memory paging).
	 */
	@Query(value = """
			select m from StockMovement m
			join fetch m.branch join fetch m.product join fetch m.actor left join fetch m.transfer
			where m.branch.id = :branchId
			  and (:productId is null or m.product.id = :productId)
			  and (:type is null or m.type = :type)
			order by m.createdAt desc, m.id desc
			""", countQuery = """
			select count(m) from StockMovement m
			where m.branch.id = :branchId
			  and (:productId is null or m.product.id = :productId)
			  and (:type is null or m.type = :type)
			""")
	Page<StockMovement> findHistory(long branchId, Long productId, MovementType type, Pageable pageable);

}
