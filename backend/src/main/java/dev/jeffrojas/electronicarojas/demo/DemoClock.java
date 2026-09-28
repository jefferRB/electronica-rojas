package dev.jeffrojas.electronicarojas.demo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DEV-ONLY clock of the demo data loader. It behaves exactly like the application clock
 * ({@code ClockConfig}: system UTC, microsecond ticks) except while the loader replays the demo
 * scenario: then it is moved to the instant of each historical operation and keeps running from
 * there, so every timestamp the real use cases write (createdAt, history, audit, outbox) is the
 * instant the operation happened in the scenario. Nothing is rewritten afterwards.
 * <p>
 * Only registered when {@code app.demo.seed=true}; production never has this bean.
 */
final class DemoClock extends Clock {

	/** Offset from the system clock; zero = real time. Shared by the zoned views of this clock. */
	private final AtomicReference<Duration> offset;

	private final ZoneId zone;

	DemoClock() {
		this(new AtomicReference<>(Duration.ZERO), ZoneOffset.UTC);
	}

	private DemoClock(AtomicReference<Duration> offset, ZoneId zone) {
		this.offset = offset;
		this.zone = zone;
	}

	/** From now on the clock reads {@code instant} and keeps ticking from it. */
	void travelTo(Instant instant) {
		offset.set(Duration.between(Instant.now(), instant));
	}

	/** Back to real time. */
	void resume() {
		offset.set(Duration.ZERO);
	}

	boolean isSimulating() {
		return !offset.get().isZero();
	}

	@Override
	public Instant instant() {
		return Instant.now().plus(offset.get()).truncatedTo(ChronoUnit.MICROS);
	}

	@Override
	public long millis() {
		return instant().toEpochMilli();
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId newZone) {
		return newZone.equals(zone) ? this : new DemoClock(offset, newZone);
	}

}
