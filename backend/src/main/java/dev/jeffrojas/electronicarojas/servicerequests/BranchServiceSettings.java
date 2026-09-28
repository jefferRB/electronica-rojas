package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import dev.jeffrojas.electronicarojas.users.AppUser;

/** Scheduling defaults a branch can adjust: estimated visit length and margin between visits. */
@Entity
@Table(name = "branch_service_settings")
public class BranchServiceSettings {

	/** Used while a branch has no row. */
	static final int DEFAULT_VISIT_MINUTES = 90;

	static final int DEFAULT_BUFFER_MINUTES = 30;

	@Id
	private Long branchId;

	@Column(nullable = false)
	private int defaultVisitMinutes;

	@Column(nullable = false)
	private int bufferMinutes;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "updated_by", nullable = false)
	private AppUser updatedBy;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected BranchServiceSettings() {
	}

	BranchServiceSettings(long branchId, int defaultVisitMinutes, int bufferMinutes, AppUser updatedBy, Instant now) {
		this.branchId = branchId;
		update(defaultVisitMinutes, bufferMinutes, updatedBy, now);
	}

	void update(int defaultVisitMinutes, int bufferMinutes, AppUser updatedBy, Instant now) {
		this.defaultVisitMinutes = defaultVisitMinutes;
		this.bufferMinutes = bufferMinutes;
		this.updatedBy = updatedBy;
		this.updatedAt = now;
	}

	public Long getBranchId() {
		return branchId;
	}

	public int getDefaultVisitMinutes() {
		return defaultVisitMinutes;
	}

	public int getBufferMinutes() {
		return bufferMinutes;
	}

	public long getVersion() {
		return version;
	}

}
