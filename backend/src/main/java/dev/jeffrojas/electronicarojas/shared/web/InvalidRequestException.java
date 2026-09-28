package dev.jeffrojas.electronicarojas.shared.web;

import java.io.Serial;

/**
 * 400 for business validation that Jakarta annotations cannot express (ENG-032). {@code code} is a
 * stable identifier the UI translates (the message is technical English).
 */
public class InvalidRequestException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String field;

	private final String code;

	public InvalidRequestException(String field, String message) {
		this(field, "INVALID", message);
	}

	public InvalidRequestException(String field, String code, String message) {
		super(message);
		this.field = field;
		this.code = code;
	}

	public String field() {
		return field;
	}

	public String code() {
		return code;
	}

}
