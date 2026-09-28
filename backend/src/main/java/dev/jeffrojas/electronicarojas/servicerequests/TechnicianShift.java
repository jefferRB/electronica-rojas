package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType;

import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.users.AppUser;

/**
 * A technician's working hours on one weekday, at one branch, with an optional break
 * (BR-SRV-005). Local times in America/Costa_Rica.
 * <p>
 * The TIME columns are bound as plain {@code LocalTime} ({@link LocalTimeJdbcType}):
 * {@code hibernate.jdbc.time_zone=UTC} would otherwise shift wall-clock times by the JVM offset.
 */
@Entity
@Table(name = "technician_shifts")
public class TechnicianShift {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "technician_id", nullable = false, updatable = false)
	private AppUser technician;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@Column(nullable = false, updatable = false)
	private short dayOfWeek;

	@JdbcType(LocalTimeJdbcType.class)
	@Column(nullable = false)
	private LocalTime startTime;

	@JdbcType(LocalTimeJdbcType.class)
	@Column(nullable = false)
	private LocalTime endTime;

	@JdbcType(LocalTimeJdbcType.class)
	private LocalTime breakStart;

	@JdbcType(LocalTimeJdbcType.class)
	private LocalTime breakEnd;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "updated_by", nullable = false)
	private AppUser updatedBy;

	@Column(nullable = false)
	private Instant updatedAt;

	protected TechnicianShift() {
	}

	TechnicianShift(AppUser technician, DayOfWeek day, Branch branch, WorkingHours hours, AppUser updatedBy,
			Instant now) {
		this.technician = technician;
		this.dayOfWeek = (short) day.getValue();
		update(branch, hours, updatedBy, now);
	}

	void update(Branch branch, WorkingHours hours, AppUser updatedBy, Instant now) {
		this.branch = branch;
		this.startTime = hours.start();
		this.endTime = hours.end();
		this.breakStart = hours.breakStart();
		this.breakEnd = hours.breakEnd();
		this.updatedBy = updatedBy;
		this.updatedAt = now;
	}

	WorkingHours hours() {
		return new WorkingHours(startTime, endTime, breakStart, breakEnd);
	}

	public Long getId() {
		return id;
	}

	public AppUser getTechnician() {
		return technician;
	}

	public Branch getBranch() {
		return branch;
	}

	public DayOfWeek getDayOfWeek() {
		return DayOfWeek.of(dayOfWeek);
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public LocalTime getBreakStart() {
		return breakStart;
	}

	public LocalTime getBreakEnd() {
		return breakEnd;
	}

}
