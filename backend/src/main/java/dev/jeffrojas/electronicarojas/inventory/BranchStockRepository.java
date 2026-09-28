package dev.jeffrojas.electronicarojas.inventory;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface BranchStockRepository extends JpaRepository<BranchStock, Long> {

	/**
	 * Race-safe creation of a missing (branch, product) row (ARCH-DB-004). If two transactions
	 * insert the same pair, the unique constraint makes the second wait and then do nothing.
	 */
	@Modifying
	@Query(value = """
			INSERT INTO branch_stock (branch_id, product_id, quantity, minimum_quantity, updated_at, version)
			VALUES (:branchId, :productId, 0, 0, :now, 0)
			ON CONFLICT (branch_id, product_id) DO NOTHING
			""", nativeQuery = true)
	int insertIfMissing(long branchId, long productId, Instant now);

	/**
	 * SELECT ... FOR UPDATE on the rows of one product, ordered by branch id. Every operation locks
	 * in this global order (branch_id, product_id), so two opposite transfers cannot deadlock
	 * (ARCH-DB-005).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from BranchStock s where s.product.id = :productId and s.branch.id in :branchIds order by s.branch.id")
	List<BranchStock> lockForUpdate(long productId, Collection<Long> branchIds);

	@Query("select s from BranchStock s join fetch s.branch where s.product.id = :productId and s.branch.id in :branchIds")
	List<BranchStock> findForProduct(long productId, Collection<Long> branchIds);

	/** FR-INV-001: the catalog seen from one branch, with 0 for products never stocked there. */
	@Query(value = """
			select new dev.jeffrojas.electronicarojas.inventory.StockItemView(
			    p.id, p.sku, p.name, p.category, p.kind, p.active,
			    coalesce(s.quantity, 0), coalesce(s.minimumQuantity, 0), s.updatedAt)
			from Product p
			left join BranchStock s on s.product = p and s.branch.id = :branchId
			where (:pattern is null or lower(p.sku) like :pattern escape '\\' or lower(p.name) like :pattern escape '\\')
			  and (:category is null or p.category = :category)
			  and (:kind is null or p.kind = :kind)
			  and (:active is null or p.active = :active)
			  and (:stockStatus is null
			       or (:stockStatus in ('OUT_OF_STOCK', 'ATTENTION') and coalesce(s.quantity, 0) = 0)
			       or (:stockStatus in ('LOW', 'ATTENTION') and coalesce(s.quantity, 0) > 0
			           and coalesce(s.minimumQuantity, 0) > coalesce(s.quantity, 0)))
			order by p.sku
			""", countQuery = """
			select count(p)
			from Product p
			left join BranchStock s on s.product = p and s.branch.id = :branchId
			where (:pattern is null or lower(p.sku) like :pattern escape '\\' or lower(p.name) like :pattern escape '\\')
			  and (:category is null or p.category = :category)
			  and (:kind is null or p.kind = :kind)
			  and (:active is null or p.active = :active)
			  and (:stockStatus is null
			       or (:stockStatus in ('OUT_OF_STOCK', 'ATTENTION') and coalesce(s.quantity, 0) = 0)
			       or (:stockStatus in ('LOW', 'ATTENTION') and coalesce(s.quantity, 0) > 0
			           and coalesce(s.minimumQuantity, 0) > coalesce(s.quantity, 0)))
			""")
	Page<StockItemView> searchBranchStock(long branchId, String pattern, String category, ProductKind kind,
			Boolean active, String stockStatus, Pageable pageable);

	/**
	 * Consolidated view, step 1: one page of catalog products, filtered in SQL. The stock-status
	 * filter looks only at {@code branchIds} (the user's scope); a branch without a stock row counts
	 * as 0 units, hence "fewer rows with units than branches" means out of stock somewhere.
	 */
	@Query(value = """
			select p from Product p
			where (:pattern is null or lower(p.sku) like :pattern escape '\\' or lower(p.name) like :pattern escape '\\')
			  and (:category is null or p.category = :category)
			  and (:kind is null or p.kind = :kind)
			  and (:active is null or p.active = :active)
			  and (:stockStatus is null
			       or (:stockStatus in ('OUT_OF_STOCK', 'ATTENTION')
			           and (select count(s) from BranchStock s
			                where s.product = p and s.branch.id in :branchIds and s.quantity > 0) < :branchCount)
			       or (:stockStatus in ('LOW', 'ATTENTION')
			           and exists (select 1 from BranchStock s where s.product = p and s.branch.id in :branchIds
			                       and s.quantity > 0 and s.minimumQuantity > s.quantity)))
			order by p.sku
			""")
	Page<Product> searchOverview(Collection<Long> branchIds, long branchCount, String pattern, String category,
			ProductKind kind, Boolean active, String stockStatus, Pageable pageable);

	/** Consolidated view, step 2: the stock cells of that page only, in one query (no N+1). */
	@Query("""
			select new dev.jeffrojas.electronicarojas.inventory.StockCellView(s.product.id, s.branch.id, s.quantity, s.minimumQuantity)
			from BranchStock s
			where s.product.id in :productIds and s.branch.id in :branchIds
			""")
	List<StockCellView> findCells(Collection<Long> productIds, Collection<Long> branchIds);

}
