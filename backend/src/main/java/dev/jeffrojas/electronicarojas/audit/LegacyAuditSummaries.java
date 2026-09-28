package dev.jeffrojas.electronicarojas.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the structured data back out of the English summaries written before V5 (Phase 2), so old
 * events can be shown in Spanish like new ones. Only the exact formats this application produced
 * are recognized; anything else yields an empty map and the UI falls back to a generic description.
 * Nothing is invented: branch names were not stored then, so only codes are returned.
 */
final class LegacyAuditSummaries {

	private record Format(AuditAction action, Pattern pattern, Function<Matcher, Map<String, Object>> extractor) {
	}

	private static final String CODE = "([A-Z0-9][A-Z0-9._-]*)";

	private static final List<Format> FORMATS = List.of(
			new Format(AuditAction.BRANCH_CREATED, Pattern.compile("Branch " + CODE + " created"),
					m -> map("branchCode", m.group(1))),
			new Format(AuditAction.BRANCH_UPDATED, Pattern.compile("Branch " + CODE + " updated \\(active=(true|false)\\)"),
					m -> map("branchCode", m.group(1), "active", Boolean.parseBoolean(m.group(2)))),
			new Format(AuditAction.USER_CREATED, Pattern.compile("Initial administrator created by bootstrap"),
					m -> map("role", "ADMIN", "bootstrap", true)),
			new Format(AuditAction.USER_CREATED, Pattern.compile("Account created with role ([A-Z_]+) and (\\d+) branch\\(es\\)"),
					m -> map("role", m.group(1), "branchCount", Integer.parseInt(m.group(2)))),
			new Format(AuditAction.USER_UPDATED,
					Pattern.compile("Account updated: role ([A-Z_]+), active=(true|false), (\\d+) branch\\(es\\)"),
					m -> map("role", m.group(1), "active", Boolean.parseBoolean(m.group(2)), "branchCount",
							Integer.parseInt(m.group(3)))),
			new Format(AuditAction.USER_PASSWORD_RESET, Pattern.compile("Password reset by (an )?administrator"),
					m -> map()),
			new Format(AuditAction.PRODUCT_CREATED, Pattern.compile("Product " + CODE + " created \\(([A-Z_]+)\\)"),
					m -> map("sku", m.group(1), "kind", m.group(2))),
			new Format(AuditAction.PRODUCT_UPDATED, Pattern.compile("Product " + CODE + " updated \\(active=(true|false)\\)"),
					m -> map("sku", m.group(1), "active", Boolean.parseBoolean(m.group(2)))),
			new Format(AuditAction.STOCK_MINIMUM_CHANGED,
					Pattern.compile("Minimum of " + CODE + " at " + CODE + " changed from (\\d+) to (\\d+)"),
					m -> map("sku", m.group(1), "branchCode", m.group(2), "previousMinimum", Integer.parseInt(m.group(3)),
							"newMinimum", Integer.parseInt(m.group(4)))),
			new Format(AuditAction.STOCK_MOVEMENT_RECORDED,
					Pattern.compile("([A-Z_]+) (-?\\d+) x " + CODE + " at " + CODE + " \\(balance (\\d+) -> (\\d+)\\)"),
					m -> map("movementType", m.group(1), "quantity", Math.abs(Integer.parseInt(m.group(2))), "sku",
							m.group(3), "branchCode", m.group(4), "balanceBefore", Integer.parseInt(m.group(5)),
							"balanceAfter", Integer.parseInt(m.group(6)))),
			new Format(AuditAction.STOCK_TRANSFER_COMPLETED,
					Pattern.compile("Transfer of (\\d+) x " + CODE + " from " + CODE + " to " + CODE
							+ " \\((sent|received), balance (\\d+)\\)"),
					m -> map("quantity", Integer.parseInt(m.group(1)), "sku", m.group(2), "sourceBranchCode", m.group(3),
							"destinationBranchCode", m.group(4), "direction",
							"sent".equals(m.group(5)) ? "SENT" : "RECEIVED", "balanceAfter",
							Integer.parseInt(m.group(6)))));

	private LegacyAuditSummaries() {
	}

	/** Structured data of a legacy summary, or an empty map when the format is not recognized. */
	static Map<String, Object> parse(String action, String summary) {
		for (Format format : FORMATS) {
			if (format.action().name().equals(action)) {
				Matcher matcher = format.pattern().matcher(summary);
				if (matcher.matches()) {
					return format.extractor().apply(matcher);
				}
			}
		}
		return Map.of();
	}

	private static Map<String, Object> map(Object... keyValues) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			result.put((String) keyValues[i], keyValues[i + 1]);
		}
		return result;
	}

}
