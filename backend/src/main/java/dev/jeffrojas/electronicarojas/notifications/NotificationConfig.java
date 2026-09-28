package dev.jeffrojas.electronicarojas.notifications;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wiring of the notifications module. The transport is chosen by configuration: the development
 * inbox unless {@code mail.mode=smtp}, which fails fast at startup when no SMTP server is configured
 * (no silent fallback that would pretend messages were sent).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProperties.class)
class NotificationConfig {

	@Bean
	MailTransport mailTransport(NotificationProperties properties, ObjectProvider<JavaMailSender> sender, Clock clock) {
		return switch (properties.mail().mode()) {
			case "inbox" -> new InboxMailTransport(clock);
			case "smtp" -> {
				JavaMailSender smtp = sender.getIfAvailable();
				if (smtp == null) {
					throw new IllegalStateException(
							"app.notifications.mail.mode=smtp requires spring.mail.host (and credentials) to be set");
				}
				yield new SmtpMailTransport(smtp, properties.mail().from());
			}
			default -> throw new IllegalStateException(
					"Unknown app.notifications.mail.mode '" + properties.mail().mode() + "' (use inbox or smtp)");
		};
	}

	/** Scheduling only when the worker is enabled (tests run the worker explicitly). */
	@Configuration(proxyBeanMethods = false)
	@EnableScheduling
	@ConditionalOnProperty(name = "app.notifications.worker-enabled", havingValue = "true", matchIfMissing = true)
	static class WorkerScheduling {

	}

}
