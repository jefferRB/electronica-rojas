package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import dev.jeffrojas.electronicarojas.shared.ClockConfig;

/**
 * Availability arithmetic (BR-SRV-005/006), pure Java so every edge is unit-tested:
 * <ul>
 * <li>a visit must fit inside the technician's shift of that local day and not touch the break;</li>
 * <li>its blocked range is [start, end + margin); blocked ranges of CONFIRMED / IN_PROGRESS
 * visits of the same technician may not overlap (touching is fine);</li>
 * <li>PROPOSED visits are tentative and never block anybody.</li>
 * </ul>
 */
final class ScheduleRules {

	static final ZoneId ZONE = ClockConfig.BUSINESS_ZONE;

	/** Granularity of the free start times suggested to the UI. */
	static final int SLOT_STEP_MINUTES = 30;

	private ScheduleRules() {
	}

	/** [start, until) of something that occupies the technician. */
	record Busy(Instant start, Instant until) {
	}

	static boolean overlaps(Instant startA, Instant untilA, Instant startB, Instant untilB) {
		return startA.isBefore(untilB) && startB.isBefore(untilA);
	}

	/** The weekday (in Costa Rica) whose shift applies to a visit starting at {@code start}. */
	static LocalDate localDate(Instant start) {
		return start.atZone(ZONE).toLocalDate();
	}

	/** Null when the visit fits the shift, otherwise the error code explaining why not. */
	static String shiftViolation(Instant start, Instant end, WorkingHours hours) {
		ZonedDateTime localStart = start.atZone(ZONE);
		ZonedDateTime localEnd = end.atZone(ZONE);
		if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) {
			return "OUTSIDE_WORKING_HOURS";
		}
		LocalTime from = localStart.toLocalTime();
		LocalTime to = localEnd.toLocalTime();
		if (from.isBefore(hours.start()) || to.isAfter(hours.end())) {
			return "OUTSIDE_WORKING_HOURS";
		}
		if (hours.hasBreak() && from.isBefore(hours.breakEnd()) && hours.breakStart().isBefore(to)) {
			return "DURING_BREAK";
		}
		return null;
	}

	/**
	 * Start times, every {@value #SLOT_STEP_MINUTES} minutes from the shift start, where a visit
	 * of {@code duration} fits the shift, its blocked range (plus {@code buffer}) does not collide
	 * with {@code busy}, and the start is not before {@code notBefore}.
	 */
	static List<LocalTime> freeStarts(LocalDate date, WorkingHours hours, List<Busy> busy, Duration duration,
			Duration buffer, Instant notBefore) {
		List<LocalTime> starts = new ArrayList<>();
		int shiftEnd = hours.end().toSecondOfDay() / 60;
		long length = duration.toMinutes();
		for (int minute = hours.start().toSecondOfDay() / 60; minute + length <= shiftEnd; minute += SLOT_STEP_MINUTES) {
			LocalTime candidate = LocalTime.ofSecondOfDay(minute * 60L);
			Instant start = date.atTime(candidate).atZone(ZONE).toInstant();
			Instant end = start.plus(duration);
			Instant until = end.plus(buffer);
			boolean free = !start.isBefore(notBefore) && shiftViolation(start, end, hours) == null
					&& busy.stream().noneMatch(b -> overlaps(start, until, b.start(), b.until()));
			if (free) {
				starts.add(candidate);
			}
		}
		return starts;
	}

}
