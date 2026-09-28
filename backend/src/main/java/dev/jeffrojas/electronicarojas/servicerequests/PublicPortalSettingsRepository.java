package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * The portal row plus its append-only slug history (native SQL: the history has no behavior of its
 * own, so it needs no entity).
 */
interface PublicPortalSettingsRepository extends JpaRepository<PublicPortalSettings, Integer> {

	@Query(value = "SELECT EXISTS (SELECT 1 FROM public_portal_slug_history WHERE slug = :slug)", nativeQuery = true)
	boolean isRetiredSlug(String slug);

	/** Previous addresses, most recently replaced first. */
	@Query(value = """
			SELECT slug FROM public_portal_slug_history
			GROUP BY slug
			ORDER BY max(changed_at) DESC
			""", nativeQuery = true)
	List<String> retiredSlugs();

	@Modifying
	@Query(value = """
			INSERT INTO public_portal_slug_history (slug, replaced_by, changed_by, changed_at)
			VALUES (:slug, :replacedBy, :changedBy, :changedAt)
			""", nativeQuery = true)
	void retireSlug(String slug, String replacedBy, long changedBy, Instant changedAt);

}
