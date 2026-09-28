package dev.jeffrojas.electronicarojas.servicerequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jeffrojas.electronicarojas.servicerequests.ScheduleRules.Busy;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;

/** BR-SRV-005/006: shift, break, margin and overlap arithmetic in Costa Rica time. */
class ScheduleRulesTest {

	private static final LocalDate DAY = LocalDate.of(2030, 3, 4); // a Monday

	private static final WorkingHours HOURS = new WorkingHours(LocalTime.of(8, 0), LocalTime.of(17, 0), LocalTime.of(12, 0),
			LocalTime.of(13, 0));

	private static Instant at(String time) {
		return DAY.atTime(LocalTime.parse(time)).atZone(ScheduleRules.ZONE).toInstant();
	}

	@Test
	void costaRicaIsUtcMinusSix() {
		assertThat(at("09:00")).isEqualTo(Instant.parse("2030-03-04T15:00:00Z"));
		assertThat(ScheduleRules.localDate(Instant.parse("2030-03-05T05:59:00Z"))).isEqualTo(DAY);
	}

	@Test
	void visitMustFitTheShiftAndAvoidTheBreak() {
		assertThat(ScheduleRules.shiftViolation(at("08:00"), at("09:30"), HOURS)).isNull();
		assertThat(ScheduleRules.shiftViolation(at("15:30"), at("17:00"), HOURS)).isNull();
		assertThat(ScheduleRules.shiftViolation(at("10:30"), at("12:00"), HOURS)).isNull(); // ends as the break starts
		assertThat(ScheduleRules.shiftViolation(at("07:30"), at("09:00"), HOURS)).isEqualTo("OUTSIDE_WORKING_HOURS");
		assertThat(ScheduleRules.shiftViolation(at("16:00"), at("17:30"), HOURS)).isEqualTo("OUTSIDE_WORKING_HOURS");
		assertThat(ScheduleRules.shiftViolation(at("11:30"), at("12:30"), HOURS)).isEqualTo("DURING_BREAK");
		assertThat(ScheduleRules.shiftViolation(at("12:00"), at("13:00"), HOURS)).isEqualTo("DURING_BREAK");
		assertThat(ScheduleRules.shiftViolation(at("23:00"), at("23:00").plus(Duration.ofHours(2)), HOURS))
			.isEqualTo("OUTSIDE_WORKING_HOURS");
	}

	@Test
	void touchingRangesDoNotOverlap() {
		assertThat(ScheduleRules.overlaps(at("09:00"), at("11:00"), at("11:00"), at("12:00"))).isFalse();
		assertThat(ScheduleRules.overlaps(at("09:00"), at("11:00"), at("10:59"), at("12:00"))).isTrue();
		assertThat(ScheduleRules.overlaps(at("09:00"), at("11:00"), at("08:00"), at("09:01"))).isTrue();
	}

	@Test
	void freeStartsSkipTheBreakConfirmedVisitsAndTheirMargin() {
		// Confirmed 09:00-10:30, blocked until 11:00 (30 min margin).
		List<Busy> busy = List.of(new Busy(at("09:00"), at("11:00")));
		List<LocalTime> free = ScheduleRules.freeStarts(DAY, HOURS, busy, Duration.ofMinutes(60), Duration.ofMinutes(30),
				Instant.EPOCH);

		// 08:00 would be blocked until 09:30 → collides. 11:00 is the first free start after it.
		assertThat(free).contains(LocalTime.of(11, 0), LocalTime.of(13, 0), LocalTime.of(16, 0))
			.doesNotContain(LocalTime.of(8, 0), LocalTime.of(9, 30), LocalTime.of(10, 30), LocalTime.of(11, 30),
					LocalTime.of(12, 0), LocalTime.of(16, 30));
	}

	@Test
	void freeStartsNeverOfferThePast() {
		List<LocalTime> free = ScheduleRules.freeStarts(DAY, HOURS, List.of(), Duration.ofMinutes(60), Duration.ZERO,
				at("14:10"));
		assertThat(free).first().isEqualTo(LocalTime.of(14, 30));
	}

	@Test
	void workingHoursRejectInvalidShapes() {
		assertThatThrownBy(() -> new WorkingHours(LocalTime.of(17, 0), LocalTime.of(8, 0), null, null))
			.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> new WorkingHours(LocalTime.of(8, 0), LocalTime.of(17, 0), LocalTime.of(12, 0), null))
			.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> new WorkingHours(LocalTime.of(8, 0), LocalTime.of(17, 0), LocalTime.of(16, 0),
				LocalTime.of(18, 0))).isInstanceOf(InvalidRequestException.class);
	}

}
