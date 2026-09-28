package dev.jeffrojas.electronicarojas.notifications;

import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * SMTP adapter, active only with {@code app.notifications.mail.mode=smtp} and a configured
 * {@code spring.mail.*} server (host, port, credentials from environment variables or an external
 * secret store; never from Git). Plain-text UTF-8 messages with a stable Message-ID.
 */
class SmtpMailTransport implements MailTransport {

	private final JavaMailSender sender;

	private final String from;

	SmtpMailTransport(JavaMailSender sender, String from) {
		this.sender = sender;
		this.from = from;
	}

	@Override
	public String send(OutgoingMail mail) throws MailDeliveryException {
		try {
			MimeMessage message = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
			helper.setFrom(from);
			helper.setTo(mail.to());
			helper.setSubject(mail.subject());
			helper.setText(mail.body(), false);
			message.saveChanges();
			message.setHeader("Message-ID", mail.messageId());
			sender.send(message);
			return mail.messageId();
		}
		catch (MailParseException | MessagingException ex) {
			// The message or address is malformed: sending it again cannot succeed.
			throw new MailDeliveryException("MESSAGE_INVALID", "The message or recipient is invalid", true, ex);
		}
		catch (MailAuthenticationException ex) {
			// A configuration problem; retrying later may succeed once it is fixed.
			throw new MailDeliveryException("SMTP_AUTH", "SMTP authentication failed", false, ex);
		}
		catch (MailSendException ex) {
			throw new MailDeliveryException("SMTP_SEND", "The SMTP server did not accept the message", false, ex);
		}
		catch (MailException ex) {
			throw new MailDeliveryException("SMTP_ERROR", "SMTP error", false, ex);
		}
	}

}
