package dev.jeffrojas.electronicarojas.inventory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.branches.Branch;

/**
 * The single place where stock rows are locked before being changed (ENG-015, ARCH-DB-002/004/005).
 * <ol>
 * <li>Missing rows are created with a race-safe upsert.</li>
 * <li>All rows involved are locked with SELECT ... FOR UPDATE in ascending branch order.</li>
 * </ol>
 * While the caller's transaction is open, any other operation on the same rows waits, so a balance
 * that was read under the lock is still true when the new balance is written.
 */
@Component
class StockLedger {

	private final BranchStockRepository stocks;

	private final Clock clock;

	StockLedger(BranchStockRepository stocks, Clock clock) {
		this.stocks = stocks;
		this.clock = clock;
	}

	/** Returns the locked rows keyed by branch id. Requires the caller's transaction. */
	@Transactional(propagation = Propagation.MANDATORY)
	Map<Long, BranchStock> lock(Product product, Branch... branches) {
		List<Long> branchIds = Stream.of(branches).map(Branch::getId).distinct().sorted().toList();
		Instant now = clock.instant();
		for (Long branchId : branchIds) {
			stocks.insertIfMissing(branchId, product.getId(), now);
		}
		return stocks.lockForUpdate(product.getId(), branchIds)
			.stream()
			.collect(Collectors.toMap(stock -> stock.getBranch().getId(), Function.identity()));
	}

}
