package dev.jeffrojas.electronicarojas.inventory;

/**
 * Result of an idempotent operation: {@code replayed} is true when the operationId had already
 * been applied and the stored result is returned instead (HTTP 200 instead of 201).
 */
public record OperationResult<T>(T body, boolean replayed) {

	public static <T> OperationResult<T> created(T body) {
		return new OperationResult<>(body, false);
	}

	public static <T> OperationResult<T> replayed(T body) {
		return new OperationResult<>(body, true);
	}

}
