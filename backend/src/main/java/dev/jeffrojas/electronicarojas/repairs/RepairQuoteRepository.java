package dev.jeffrojas.electronicarojas.repairs;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dev.jeffrojas.electronicarojas.repairs.RepairEnums.QuoteStatus;

interface RepairQuoteRepository extends JpaRepository<RepairQuote, Long> {

	@Query("select q from RepairQuote q join fetch q.createdBy left join fetch q.decidedBy where q.order.id = :orderId order by q.createdAt, q.id")
	List<RepairQuote> findForOrder(long orderId);

	boolean existsByOrderIdAndStatus(long orderId, QuoteStatus status);

	Optional<RepairQuote> findByIdAndOrderId(long id, long orderId);

}
