package dev.jeffrojas.electronicarojas.shared.web;

import java.io.Serial;

/**
 * 404. Also used when a resource exists but is outside the caller's scope, so a collaborator
 * cannot tell "not yours" from "does not exist" (ER-FS-001 section 12).
 */
public class NotFoundException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public NotFoundException(String message) {
		super(message);
	}

}
