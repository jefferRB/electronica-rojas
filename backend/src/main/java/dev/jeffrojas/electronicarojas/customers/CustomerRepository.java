package dev.jeffrojas.electronicarojas.customers;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Customer scope for non-admin roles: customers registered at one of the user's active branches,
 * or with a repair order at one of them. Evaluated in SQL (native, so this module does not depend
 * on the repairs entities) before paging, never after.
 */
interface CustomerRepository extends JpaRepository<Customer, Long> {

	String SCOPE = """
			(CAST(:admin AS BOOLEAN)
			 OR c.registered_branch_id IN (SELECT ub.branch_id FROM user_branches ub
			                               JOIN branches b ON b.id = ub.branch_id
			                               WHERE ub.user_id = :userId AND b.active)
			 OR EXISTS (SELECT 1 FROM repair_orders o
			            WHERE o.customer_id = c.id
			              AND o.branch_id IN (SELECT ub.branch_id FROM user_branches ub
			                                  JOIN branches b ON b.id = ub.branch_id
			                                  WHERE ub.user_id = :userId AND b.active)))
			""";

	/** List page search: one normalized "contains" pattern on the name or e-mail, or phone digits. */
	String SEARCH = """
			(CAST(:pattern AS VARCHAR) IS NULL
			 OR c.search_name LIKE :pattern ESCAPE '\\'
			 OR c.email LIKE :pattern ESCAPE '\\'
			 OR (CAST(:digits AS VARCHAR) IS NOT NULL AND c.phone LIKE '%' || :digits || '%'))
			""";

	/** Every given word of the name appears in search_name (any order): "perez ana" finds "Ana Pérez". */
	String NAME_HIT = """
			(CAST(:t1 AS VARCHAR) IS NOT NULL AND c.search_name LIKE :t1 ESCAPE '\\'
			 AND (CAST(:t2 AS VARCHAR) IS NULL OR c.search_name LIKE :t2 ESCAPE '\\')
			 AND (CAST(:t3 AS VARCHAR) IS NULL OR c.search_name LIKE :t3 ESCAPE '\\'))
			""";

	String PHONE_HIT = "(CAST(:digits AS VARCHAR) IS NOT NULL AND c.phone LIKE '%' || :digits || '%')";

	@Query(value = "SELECT c.* FROM customers c WHERE " + SCOPE + " AND " + SEARCH
			+ " ORDER BY c.search_name, c.id",
			countQuery = "SELECT count(*) FROM customers c WHERE " + SCOPE + " AND " + SEARCH, nativeQuery = true)
	Page<Customer> search(boolean admin, long userId, String pattern, String digits, Pageable pageable);

	/**
	 * Incremental lookup for the customer picker: name words OR phone fragment, within the caller's
	 * scope only (partial terms never reveal customers of other branches). Customers matching both
	 * come first.
	 */
	@Query(value = "SELECT c.* FROM customers c WHERE " + SCOPE + " AND (" + NAME_HIT + " OR " + PHONE_HIT + ")"
			+ " ORDER BY (CASE WHEN " + NAME_HIT + " THEN 1 ELSE 0 END + CASE WHEN " + PHONE_HIT
			+ " THEN 1 ELSE 0 END) DESC, c.search_name, c.id",
			countQuery = "SELECT count(*) FROM customers c WHERE " + SCOPE + " AND (" + NAME_HIT + " OR " + PHONE_HIT
					+ ")",
			nativeQuery = true)
	Page<Customer> lookup(boolean admin, long userId, String t1, String t2, String t3, String digits,
			Pageable pageable);

	@Query(value = "SELECT count(*) > 0 FROM customers c WHERE c.id = :customerId AND " + SCOPE, nativeQuery = true)
	boolean isVisible(long customerId, boolean admin, long userId);

	/** Same normalized name within the caller's scope (duplicate detection, BR-CUS-002). */
	@Query(value = "SELECT c.* FROM customers c WHERE c.search_name = :searchName AND " + SCOPE
			+ " ORDER BY c.id LIMIT 10", nativeQuery = true)
	List<Customer> findSameNameInScope(String searchName, boolean admin, long userId);

	/** Exact phone matches in the whole company (duplicate detection, BR-CUS-002). */
	List<Customer> findTop10ByPhoneOrderByIdAsc(String phone);

}
