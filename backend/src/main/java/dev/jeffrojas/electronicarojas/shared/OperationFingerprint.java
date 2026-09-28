package dev.jeffrojas.electronicarojas.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SHA-256 of the business fields of a request. Stored with an idempotent operation (stock
 * movements, transfers, repair intake) so that a retry with the same operationId can be told apart
 * from a different request that reuses the id (BR-TRF-003).
 */
public final class OperationFingerprint {

	private OperationFingerprint() {
	}

	public static String of(Object... parts) {
		// Unit Separator cannot appear in the parts, so ("a", "bc") and ("ab", "c") differ.
		String canonical = Stream.of(parts).map(part -> Objects.toString(part, "")).collect(Collectors.joining("\u001F"));
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every Java platform", ex);
		}
	}

}
