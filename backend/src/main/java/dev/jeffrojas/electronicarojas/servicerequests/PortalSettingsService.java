package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import jakarta.persistence.EntityManager;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.audit.AuditAction;
import dev.jeffrojas.electronicarojas.audit.AuditEntry;
import dev.jeffrojas.electronicarojas.audit.AuditService;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.servicerequests.PublicPortalSettings.Rules;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PortalSettingsRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PortalSettingsView;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PortalSlugRequest;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PublicBranch;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PublicPortal;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PublicPortalRules;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.Province;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * The shareable public home-service page (BR-SRV-009): configured by ADMIN (the portal belongs to
 * the whole company, like the catalog), read anonymously through its slug. The anonymous view
 * carries only what the page needs to render; everything administrative stays behind ADMIN.
 */
@Service
@Transactional(readOnly = true)
public class PortalSettingsService {

	private final PublicPortalSettingsRepository portals;

	private final BranchService branchService;

	private final AuditService audit;

	private final EntityManager entityManager;

	private final Clock clock;

	PortalSettingsService(PublicPortalSettingsRepository portals, BranchService branchService, AuditService audit,
			EntityManager entityManager, Clock clock) {
		this.portals = portals;
		this.branchService = branchService;
		this.audit = audit;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	// ---- Administration ----

	@PreAuthorize("hasRole('ADMIN')")
	public PortalSettingsView settings() {
		return view(current());
	}

	/** Rules, messages and the on/off switch. Turning it off keeps the address and every request. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public PortalSettingsView update(CurrentUser user, PortalSettingsRequest request) {
		PublicPortalSettings portal = current();
		requireVersion(portal, request.version());
		if (request.maxDaysAhead() < request.minNoticeDays()) {
			throw new InvalidRequestException("maxDaysAhead", "DAYS_RANGE_INVALID",
					"the latest day must not be before the minimum notice");
		}
		Rules before = portal.rules();
		Rules after = new Rules(request.enabled(), request.allowPreferredDate(), request.allowPreferredWindow(),
				request.minNoticeDays(), request.maxDaysAhead(), request.serviceDays().stream().distinct().sorted().toList(),
				List.of(Province.values()).stream().filter(request.servedProvinces()::contains).toList(),
				serviceTypes(request.serviceTypes()), plainText("welcomeMessage", request.welcomeMessage()),
				plainText("successMessage", request.successMessage()));
		portal.update(after, entityManager.getReference(AppUser.class, user.id()), clock.instant());
		portals.saveAndFlush(portal);
		List<String> changed = changedFields(before, after);
		if (!changed.isEmpty()) {
			audit.record(user, AuditEntry.of(AuditAction.PUBLIC_PORTAL_UPDATED, PublicPortalSettings.ID)
				.detail("enabled", after.enabled())
				.detail("enabledChanged", before.enabled() != after.enabled())
				.detail("changedFields", changed)
				.summary("Public portal settings updated (" + String.join(", ", changed) + ")"));
		}
		return view(portal);
	}

	/**
	 * Moves the portal to a new address. The previous slug is kept as an alias that resolves to the
	 * new one, so links and QR codes already shared keep working (the UI still warns before).
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public PortalSettingsView changeSlug(CurrentUser user, PortalSlugRequest request) {
		String slug = PortalSlugs.normalize(request.slug());
		PortalSlugs.problem(slug).ifPresent(code -> {
			throw new InvalidRequestException("slug", code, "use 3-60 lower-case letters, digits and single hyphens");
		});
		PublicPortalSettings portal = current();
		requireVersion(portal, request.version());
		if (slug.equals(portal.getSlug())) {
			return view(portal);
		}
		String previous = portal.getSlug();
		Instant now = clock.instant();
		portal.changeSlug(slug, entityManager.getReference(AppUser.class, user.id()), now);
		portals.saveAndFlush(portal);
		portals.retireSlug(previous, slug, user.id(), now);
		audit.record(user, AuditEntry.of(AuditAction.PUBLIC_PORTAL_SLUG_CHANGED, PublicPortalSettings.ID)
			.detail("previousSlug", previous)
			.detail("newSlug", slug)
			.summary("Public portal address changed from " + previous + " to " + slug));
		return view(portal);
	}

	// ---- Anonymous ----

	/**
	 * The page for a slug: the current one or a previous one (the answer carries the current slug so
	 * the browser can show the canonical address). {@code null} = the current portal (old links
	 * without a slug). An unknown slug is 404.
	 */
	public PublicPortal publicPortal(String slug) {
		PublicPortalSettings portal = current();
		if (slug != null) {
			String wanted = PortalSlugs.normalize(slug);
			if (!wanted.equals(portal.getSlug())
					&& (wanted.length() > PortalSlugs.MAX_LENGTH || !portals.isRetiredSlug(wanted))) {
				throw new NotFoundException("Portal not found.");
			}
		}
		if (!portal.isEnabled()) {
			return new PublicPortal(portal.getSlug(), false, portal.getWelcomeMessage(), null, null, List.of());
		}
		Rules rules = portal.rules();
		LocalDate today = today();
		return new PublicPortal(portal.getSlug(), true, portal.getWelcomeMessage(), portal.getSuccessMessage(),
				new PublicPortalRules(rules.allowPreferredDate(), rules.allowPreferredWindow(),
						PortalRules.earliestDate(rules, today), PortalRules.latestDate(rules, today), rules.serviceDays(),
						rules.servedProvinces(), rules.serviceTypes()),
				branchService.activeBranches().stream().map(branch -> new PublicBranch(branch.getId(), branch.getName())).toList());
	}

	/** The rules a submission must follow now; 409 while the portal is off (nothing is stored). */
	Rules acceptingRules() {
		PublicPortalSettings portal = current();
		if (!portal.isEnabled()) {
			throw new ConflictException("PORTAL_DISABLED", "The public request form is not accepting requests right now.");
		}
		return portal.rules();
	}

	LocalDate today() {
		return LocalDate.now(clock.withZone(ScheduleRules.ZONE));
	}

	private PublicPortalSettings current() {
		return portals.findById(PublicPortalSettings.ID)
			.orElseThrow(() -> new IllegalStateException("public_portal_settings row missing (created by V12)"));
	}

	private static void requireVersion(PublicPortalSettings portal, long version) {
		if (portal.getVersion() != version) {
			throw new ConflictException("STALE_VERSION", "The portal settings were changed by someone else. Reload them.");
		}
	}

	private PortalSettingsView view(PublicPortalSettings portal) {
		List<String> previous = portals.retiredSlugs().stream().filter(slug -> !slug.equals(portal.getSlug())).toList();
		return new PortalSettingsView(portal.isEnabled(), portal.getSlug(), portal.isAllowPreferredDate(),
				portal.isAllowPreferredWindow(), portal.getMinNoticeDays(), portal.getMaxDaysAhead(),
				portal.getServiceDays(), portal.getServedProvinces(), portal.getServiceTypes(), portal.getWelcomeMessage(),
				portal.getSuccessMessage(), previous, portal.getUpdatedAt(),
				portal.getUpdatedBy() == null ? null : ServiceViews.person(portal.getUpdatedBy()), portal.getVersion());
	}

	/**
	 * Messages are shown as plain text (React escapes them). Markup is refused rather than silently
	 * stripped, so what the administrator saves is exactly what customers read (SEC-007).
	 */
	static String plainText(String field, String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String text = value.replace("\r\n", "\n").strip();
		if (text.indexOf('<') >= 0 || text.indexOf('>') >= 0) {
			throw new InvalidRequestException(field, "HTML_NOT_ALLOWED", "plain text only");
		}
		// Control characters other than line breaks have no place in a message.
		return text.replaceAll("[\\p{Cntrl}&&[^\n]]", "");
	}

	/** Trimmed, plain text, without repeats (case-insensitive), in the administrator's order. */
	private static List<String> serviceTypes(List<String> values) {
		Map<String, String> unique = new LinkedHashMap<>();
		for (String value : values) {
			String type = plainText("serviceTypes", value);
			if (type != null) {
				unique.putIfAbsent(type.toLowerCase(Locale.ROOT), type);
			}
		}
		return List.copyOf(unique.values());
	}

	private static List<String> changedFields(Rules before, Rules after) {
		Map<String, Function<Rules, Object>> fields = new LinkedHashMap<>();
		fields.put("enabled", Rules::enabled);
		fields.put("allowPreferredDate", Rules::allowPreferredDate);
		fields.put("allowPreferredWindow", Rules::allowPreferredWindow);
		fields.put("minNoticeDays", Rules::minNoticeDays);
		fields.put("maxDaysAhead", Rules::maxDaysAhead);
		fields.put("serviceDays", Rules::serviceDays);
		fields.put("servedProvinces", Rules::servedProvinces);
		fields.put("serviceTypes", Rules::serviceTypes);
		fields.put("welcomeMessage", Rules::welcomeMessage);
		fields.put("successMessage", Rules::successMessage);
		List<String> changed = new ArrayList<>();
		fields.forEach((name, getter) -> {
			if (!Objects.equals(getter.apply(before), getter.apply(after))) {
				changed.add(name);
			}
		});
		return changed;
	}

}
