package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.LocalTime;

import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;

/** One day's working hours, with an optional break, in Costa Rica local time. */
record WorkingHours(LocalTime start, LocalTime end, LocalTime breakStart, LocalTime breakEnd) {

	WorkingHours {
		if (start == null || end == null || !end.isAfter(start)) {
			throw new InvalidRequestException("end", "SHIFT_INVALID", "the shift must end after it starts");
		}
		if ((breakStart == null) != (breakEnd == null)) {
			throw new InvalidRequestException("breakEnd", "BREAK_INVALID", "a break needs a start and an end");
		}
		if (breakStart != null
				&& (breakStart.isBefore(start) || !breakEnd.isAfter(breakStart) || breakEnd.isAfter(end))) {
			throw new InvalidRequestException("breakStart", "BREAK_INVALID", "the break must be inside the shift");
		}
	}

	boolean hasBreak() {
		return breakStart != null;
	}

}
