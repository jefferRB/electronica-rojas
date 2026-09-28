package dev.jeffrojas.electronicarojas.shared.ping;

import java.time.Instant;

/**
 * Public response of {@code GET /api/v1/ping}. Deliberately reveals nothing about
 * configuration, versions or infrastructure (ARCH-API-003).
 */
public record PingResponse(String status, Instant timestamp) {
}
