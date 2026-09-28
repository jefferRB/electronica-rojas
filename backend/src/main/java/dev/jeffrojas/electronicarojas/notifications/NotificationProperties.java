package dev.jeffrojas.electronicarojas.notifications;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.notifications.*}. Defaults are safe for development: the worker runs, and mail
 * goes to the in-memory inbox, never to real people, until {@code mail.mode=smtp} is configured.
 *
 * @param workerEnabled run the scheduled worker (tests call it directly instead)
 * @param pollInterval pause between two worker runs
 * @param batchSize messages claimed per run
 * @param maxAttempts delivery attempts before a message is FAILED
 * @param lease how long a claimed message belongs to one worker before another may retry it
 * @param mail transport settings
 * @param publicBaseUrl base URL of the public site for the customer's status link (optional)
 */
@ConfigurationProperties("app.notifications")
public record NotificationProperties(boolean workerEnabled, Duration pollInterval, int batchSize, int maxAttempts,
		Duration lease, Mail mail, String publicBaseUrl) {

	public NotificationProperties {
		pollInterval = pollInterval == null ? Duration.ofSeconds(15) : pollInterval;
		batchSize = batchSize <= 0 ? 20 : batchSize;
		maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
		lease = lease == null ? Duration.ofMinutes(2) : lease;
		mail = mail == null ? new Mail(null, null) : mail;
		publicBaseUrl = publicBaseUrl == null || publicBaseUrl.isBlank() ? null : publicBaseUrl.replaceAll("/+$", "");
	}

	/**
	 * @param mode {@code inbox} (default: development inbox, nothing leaves the process) or {@code smtp}
	 * @param from sender address
	 */
	public record Mail(String mode, String from) {

		public Mail {
			mode = mode == null || mode.isBlank() ? "inbox" : mode.strip().toLowerCase(java.util.Locale.ROOT);
			from = from == null || from.isBlank() ? "no-reply@electronica-rojas.test" : from.strip();
		}

	}

}
