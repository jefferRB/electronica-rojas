package dev.jeffrojas.electronicarojas.customers;

/** Where the customer expressed a consent decision (BR-CUS-006). */
public enum ConsentSource {
	/** At the counter, in person. */
	IN_PERSON,
	/** By phone with a collaborator. */
	PHONE,
	/** In writing (an e-mail or message from the customer). */
	WRITTEN,
	/** Channel checkboxes of the public home-service form, applied when staff links the customer. */
	PUBLIC_FORM
}
