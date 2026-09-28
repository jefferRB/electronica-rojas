package dev.jeffrojas.electronicarojas.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What a use case reports to the audit trail.
 *
 * @param branchId branch the action belongs to, used to scope who can read the event; may be null
 * @param operationId client operation id for idempotent operations; may be null
 * @param summary short technical text (English, internal); the UI renders {@code details} instead
 * @param details structured, language-neutral data (codes, quantities, name snapshots) from which
 * the UI builds the user-facing description. Never secrets or customer personal data.
 */
public record AuditEntry(AuditAction action, Object entityId, Long branchId, UUID operationId, String summary,
		Map<String, Object> details) {

	public AuditEntry {
		details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
	}

	public static Builder of(AuditAction action, Object entityId) {
		return new Builder(action, entityId);
	}

	/** Small builder: keeps call sites readable and the detail keys next to their values. */
	public static final class Builder {

		private final AuditAction action;

		private final Object entityId;

		private Long branchId;

		private UUID operationId;

		private final Map<String, Object> details = new LinkedHashMap<>();

		private Builder(AuditAction action, Object entityId) {
			this.action = action;
			this.entityId = entityId;
		}

		public Builder branch(Long branchId) {
			this.branchId = branchId;
			return this;
		}

		public Builder operation(UUID operationId) {
			this.operationId = operationId;
			return this;
		}

		/** Null values are skipped so optional data does not produce empty keys. */
		public Builder detail(String key, Object value) {
			if (value != null) {
				details.put(key, value instanceof Enum<?> e ? e.name() : value);
			}
			return this;
		}

		public AuditEntry summary(String summary) {
			return new AuditEntry(action, entityId, branchId, operationId, summary, details);
		}

	}

}
