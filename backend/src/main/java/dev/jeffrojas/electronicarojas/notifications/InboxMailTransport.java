package dev.jeffrojas.electronicarojas.notifications;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import dev.jeffrojas.electronicarojas.notifications.NotificationDtos.InboxMessage;

/**
 * Development and test transport (default): keeps the last {@value #CAPACITY} messages in memory so
 * an administrator can read exactly what a customer would receive, while nothing leaves the process.
 * Lost on restart by design. It can also simulate provider failures, to exercise retries without a
 * real server.
 */
class InboxMailTransport implements MailTransport {

	static final int CAPACITY = 100;

	private final Deque<InboxMessage> messages = new ArrayDeque<>();

	private final Clock clock;

	private int failuresToSimulate;

	private boolean simulatePermanent;

	InboxMailTransport(Clock clock) {
		this.clock = clock;
	}

	@Override
	public synchronized String send(OutgoingMail mail) throws MailDeliveryException {
		if (failuresToSimulate > 0) {
			failuresToSimulate--;
			throw new MailDeliveryException(simulatePermanent ? "SIMULATED_REJECTED" : "SIMULATED_UNAVAILABLE",
					simulatePermanent ? "Simulated permanent rejection" : "Simulated temporary failure", simulatePermanent,
					null);
		}
		messages.addFirst(new InboxMessage(mail.messageId(), mail.to(), mail.subject(), mail.body(), clock.instant()));
		while (messages.size() > CAPACITY) {
			messages.removeLast();
		}
		return mail.messageId();
	}

	/** Newest first. */
	synchronized List<InboxMessage> messages() {
		return new ArrayList<>(messages);
	}

	/** The next {@code count} sends fail (temporarily, or permanently when {@code permanent}). */
	synchronized void simulateFailures(int count, boolean permanent) {
		this.failuresToSimulate = count;
		this.simulatePermanent = permanent;
	}

	synchronized void clear() {
		messages.clear();
		failuresToSimulate = 0;
	}

}
