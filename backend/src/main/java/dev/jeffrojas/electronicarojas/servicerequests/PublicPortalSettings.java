package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.Province;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * The company's public home-service page (BR-SRV-009): a single row created by V12. Only the slug is
 * stored; the full URL is built from the site's own origin, so no domain is hard-coded. Turning the
 * portal off keeps the URL (and printed QR codes) valid: the page explains that requests are paused.
 */
@Entity
@Table(name = "public_portal_settings")
public class PublicPortalSettings {

	/** The only row (CHECK id = 1). */
	static final int ID = 1;

	@Id
	private Integer id;

	@Column(nullable = false)
	private boolean enabled;

	@Column(nullable = false, length = 60)
	private String slug;

	@Column(nullable = false)
	private boolean allowPreferredDate;

	@Column(nullable = false)
	private boolean allowPreferredWindow;

	@Column(nullable = false)
	private int minNoticeDays;

	@Column(nullable = false)
	private int maxDaysAhead;

	/** ISO weekdays, 1 = Monday. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private List<Integer> serviceDays;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private List<Province> servedProvinces;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private List<String> serviceTypes;

	@Column(length = 300)
	private String welcomeMessage;

	@Column(length = 500)
	private String successMessage;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "updated_by")
	private AppUser updatedBy;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected PublicPortalSettings() {
	}

	/** Everything the administrator can change except the address. */
	record Rules(boolean enabled, boolean allowPreferredDate, boolean allowPreferredWindow, int minNoticeDays,
			int maxDaysAhead, List<Integer> serviceDays, List<Province> servedProvinces, List<String> serviceTypes,
			String welcomeMessage, String successMessage) {
	}

	void update(Rules rules, AppUser editor, Instant now) {
		this.enabled = rules.enabled();
		this.allowPreferredDate = rules.allowPreferredDate();
		this.allowPreferredWindow = rules.allowPreferredWindow();
		this.minNoticeDays = rules.minNoticeDays();
		this.maxDaysAhead = rules.maxDaysAhead();
		this.serviceDays = List.copyOf(rules.serviceDays());
		this.servedProvinces = List.copyOf(rules.servedProvinces());
		this.serviceTypes = List.copyOf(rules.serviceTypes());
		this.welcomeMessage = rules.welcomeMessage();
		this.successMessage = rules.successMessage();
		touch(editor, now);
	}

	void changeSlug(String slug, AppUser editor, Instant now) {
		this.slug = slug;
		touch(editor, now);
	}

	Rules rules() {
		return new Rules(enabled, allowPreferredDate, allowPreferredWindow, minNoticeDays, maxDaysAhead, serviceDays,
				servedProvinces, serviceTypes, welcomeMessage, successMessage);
	}

	private void touch(AppUser editor, Instant now) {
		this.updatedBy = editor;
		this.updatedAt = now;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public String getSlug() {
		return slug;
	}

	public boolean isAllowPreferredDate() {
		return allowPreferredDate;
	}

	public boolean isAllowPreferredWindow() {
		return allowPreferredWindow;
	}

	public int getMinNoticeDays() {
		return minNoticeDays;
	}

	public int getMaxDaysAhead() {
		return maxDaysAhead;
	}

	public List<Integer> getServiceDays() {
		return serviceDays;
	}

	public List<Province> getServedProvinces() {
		return servedProvinces;
	}

	public List<String> getServiceTypes() {
		return serviceTypes;
	}

	public String getWelcomeMessage() {
		return welcomeMessage;
	}

	public String getSuccessMessage() {
		return successMessage;
	}

	public AppUser getUpdatedBy() {
		return updatedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getVersion() {
		return version;
	}

}
