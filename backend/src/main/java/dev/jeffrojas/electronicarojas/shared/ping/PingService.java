package dev.jeffrojas.electronicarojas.shared.ping;

import java.time.Clock;

import org.springframework.stereotype.Service;

@Service
public class PingService {

	private final Clock clock;

	public PingService(Clock clock) {
		this.clock = clock;
	}

	public PingResponse ping() {
		return new PingResponse("ok", clock.instant());
	}

}
