package dev.jeffrojas.electronicarojas.demo;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Random;

import dev.jeffrojas.electronicarojas.shared.ClockConfig;

/**
 * Dates of the demo scenario, relative to the day it is loaded (Costa Rica calendar). Counter and
 * workshop work happens on business days (Monday to Saturday); home visits on weekdays. Both
 * mappings are strictly monotonic, so the order of the operations never depends on the weekday the
 * scenario is loaded.
 */
final class DemoCalendar {

	private final LocalDate today;

	/** Fixed seed: loading the scenario twice on a fresh database gives the same timestamps. */
	private final Random seconds = new Random(20_260_928L);

	DemoCalendar(Instant now) {
		this.today = LocalDate.ofInstant(now, ClockConfig.BUSINESS_ZONE);
	}

	/** {@code offset} business days from today: -1 is the previous business day, 0 is today. */
	LocalDate day(int offset) {
		return plusDays(today, offset, false);
	}

	/** {@code offset} business days from {@code date}. */
	LocalDate day(LocalDate date, int offset) {
		return plusDays(date, offset, false);
	}

	/** {@code offset} weekdays from today (0 = today, or the next Monday during a weekend). */
	LocalDate weekday(int offset) {
		LocalDate start = today;
		while (offset == 0 && isWeekend(start)) {
			start = start.plusDays(1);
		}
		return plusDays(start, offset, true);
	}

	/** An operation at that local time, with a few seconds so no two look machine-made. */
	Instant at(LocalDate date, String time) {
		return date.atTime(LocalTime.parse(time).plusSeconds(3 + seconds.nextInt(55)))
			.atZone(ClockConfig.BUSINESS_ZONE)
			.toInstant();
	}

	/** An agreed time (a visit slot): exactly on the minute. */
	static Instant slot(LocalDate date, String time) {
		return date.atTime(LocalTime.parse(time)).atZone(ClockConfig.BUSINESS_ZONE).toInstant();
	}

	private static LocalDate plusDays(LocalDate from, int offset, boolean weekdaysOnly) {
		LocalDate date = from;
		int step = offset < 0 ? -1 : 1;
		for (int remaining = Math.abs(offset); remaining > 0;) {
			date = date.plusDays(step);
			boolean closed = date.getDayOfWeek() == DayOfWeek.SUNDAY
					|| (weekdaysOnly && date.getDayOfWeek() == DayOfWeek.SATURDAY);
			if (!closed) {
				remaining--;
			}
		}
		return date;
	}

	private static boolean isWeekend(LocalDate date) {
		return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
	}

}
