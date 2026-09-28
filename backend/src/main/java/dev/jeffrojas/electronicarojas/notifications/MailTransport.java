package dev.jeffrojas.electronicarojas.notifications;

/**
 * Delivery boundary of e-mail (C.4). Composition knows nothing about providers and the business
 * modules know nothing about either. Two implementations: the development inbox (default) and SMTP.
 * <p>
 * SMTP has no idempotency key: a message accepted by the server just before this process dies may
 * be sent again when the lease expires. Delivery is therefore at-least-once; the stable
 * {@code Message-ID} lets mail clients recognize the duplicate.
 */
interface MailTransport {

	/** Accepted by the provider (not proof of delivery). @return the provider or local message id */
	String send(OutgoingMail mail) throws MailDeliveryException;

	record OutgoingMail(String to, String subject, String body, String messageId) {
	}

	/** {@code permanent}: retrying cannot help (e.g. an address the server rejects). */
	final class MailDeliveryException extends Exception {

		private static final long serialVersionUID = 1L;

		private final boolean permanent;

		private final String code;

		MailDeliveryException(String code, String message, boolean permanent, Throwable cause) {
			super(message, cause);
			this.code = code;
			this.permanent = permanent;
		}

		boolean isPermanent() {
			return permanent;
		}

		String code() {
			return code;
		}

	}

}
