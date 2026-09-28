package dev.jeffrojas.electronicarojas.inventory;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

	Optional<StockTransfer> findByOperationId(UUID operationId);

	boolean existsByOperationId(UUID operationId);

}
