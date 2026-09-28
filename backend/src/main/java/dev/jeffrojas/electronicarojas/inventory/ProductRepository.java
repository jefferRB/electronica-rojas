package dev.jeffrojas.electronicarojas.inventory;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ProductRepository extends JpaRepository<Product, Long> {

	boolean existsBySku(String sku);

	/**
	 * Catalog search. {@code pattern} is an already lower-cased LIKE pattern with wildcards
	 * escaped ({@link SearchPattern}); null filters are ignored. Ordered by the unique SKU, so
	 * pagination is stable.
	 */
	@Query("""
			select p from Product p
			where (:pattern is null or lower(p.sku) like :pattern escape '\\' or lower(p.name) like :pattern escape '\\')
			  and (:category is null or p.category = :category)
			  and (:kind is null or p.kind = :kind)
			  and (:active is null or p.active = :active)
			order by p.sku
			""")
	Page<Product> search(String pattern, String category, ProductKind kind, Boolean active, Pageable pageable);

	@Query("select distinct p.category from Product p order by p.category")
	List<String> findCategories();

}
