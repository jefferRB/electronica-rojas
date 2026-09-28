package dev.jeffrojas.electronicarojas.inventory;

import java.io.Serial;
import java.util.Map;

import dev.jeffrojas.electronicarojas.shared.web.ConflictException;

/** 409 with the current balance, so the UI can show what is really available (FR-TRF-003). */
public class InsufficientStockException extends ConflictException {

	@Serial
	private static final long serialVersionUID = 1L;

	InsufficientStockException(long branchId, int available, int requested) {
		super("INSUFFICIENT_STOCK", "Insufficient stock: " + available + " available, " + requested + " requested.",
				Map.of("branchId", branchId, "available", available, "requested", requested));
	}

}
