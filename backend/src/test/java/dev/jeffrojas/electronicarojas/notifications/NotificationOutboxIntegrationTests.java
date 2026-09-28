package dev.jeffrojas.electronicarojas.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jeffrojas.electronicarojas.BrowserClient;
import dev.jeffrojas.electronicarojas.notifications.NotificationDtos.InboxMessage;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.EventType;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SubjectType;

/**
 * C.3 against PostgreSQL: the outbox row is written in the business transaction (and only if it
 * commits), the worker delivers after commit, re-checks consent, retries with a bound, never
 * duplicates what it can avoid, and several workers never take the same message.
 */
class NotificationOutboxIntegrationTests extends NotificationTestSupport {

	@LocalServerPort
	private int port;

	@Autowired
	private NotificationOutbox outbox;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void readyForPickupIsQueuedWithTheStatusChangeAndSentOnlyByTheWorker() throws Exception {
		long orderId = orderInRepair(true);
		mockMvc.perform(put(orderId, "Tarjeta madre dañada por humedad")).andExpect(status().isOk());

		transition(orderId, "READY_FOR_PICKUP").andExpect(status().isOk())
			.andExpect(jsonPath("$.notifications[0].eventType").value("REPAIR_READY_FOR_PICKUP"))
			.andExpect(jsonPath("$.notifications[0].channel").value("EMAIL"))
			.andExpect(jsonPath("$.notifications[0].status").value("PENDING"));
		// Committed, but nothing leaves the process until a worker runs.
		assertThat(inbox().messages()).isEmpty();
		assertThat(countRows("notification_outbox")).isEqualTo(1);

		assertThat(dispatcher.runOnce()).isEqualTo(1);

		assertThat(outboxStatus(orderId)).isEqualTo("SENT");
		List<InboxMessage> sent = inbox().messages();
		assertThat(sent).hasSize(1);
		assertThat(sent.get(0).to()).isEqualTo("lucia@ejemplo.test");
		assertThat(sent.get(0).subject()).matches("Tu equipo está listo para retirar · Orden OR-\\d{4}-\\d{6}");
		assertThat(sent.get(0).body()).startsWith("Hola Lucía:").doesNotContain("humedad", "Tarjeta");
		assertThat(jdbc.queryForObject("SELECT recipient_hint FROM notification_outbox", String.class))
			.isEqualTo("l***@ejemplo.test");
		assertThat(jdbc.queryForList("SELECT outcome FROM notification_attempts", String.class)).containsExactly("SENT");
		// A second run finds nothing: the message is never sent twice.
		assertThat(dispatcher.runOnce()).isZero();
		assertThat(inbox().messages()).hasSize(1);
	}

	@Test
	void withoutConsentTheEventIsRecordedAsNotNotifiedAndNothingIsSent() throws Exception {
		long orderId = orderInRepair(false);
		transition(orderId, "READY_FOR_PICKUP").andExpect(status().isOk())
			.andExpect(jsonPath("$.notifications[0].status").value("SKIPPED"))
			.andExpect(jsonPath("$.notifications[0].skipReason").value("NO_CONSENT"));

		assertThat(dispatcher.runOnce()).isZero();
		assertThat(inbox().messages()).isEmpty();
	}

	@Test
	void aFailedBusinessTransactionLeavesNoNotification() throws Exception {
		long orderId = orderInRepair(true);
		jdbc.execute("""
				CREATE FUNCTION test_fail_audit() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN
				  IF NEW.action = 'REPAIR_STATUS_CHANGED' AND NEW.details->>'toStatus' = 'READY_FOR_PICKUP' THEN
				    RAISE EXCEPTION 'simulated failure';
				  END IF;
				  RETURN NEW;
				END; $$
				""");
		jdbc.execute("CREATE TRIGGER test_fail_audit BEFORE INSERT ON audit_events FOR EACH ROW EXECUTE FUNCTION test_fail_audit()");
		try (BrowserClient browser = BrowserClient.login(port, "tecnico@electronica-rojas.test", USER_PASSWORD)) {
			assertThat(browser.postJson("/api/v1/repair-orders/" + orderId + "/status",
					"{\"toStatus\":\"READY_FOR_PICKUP\"}").status()).isEqualTo(500);
		}
		finally {
			jdbc.execute("DROP TRIGGER test_fail_audit ON audit_events");
			jdbc.execute("DROP FUNCTION test_fail_audit()");
		}
		assertThat(countRows("notification_outbox")).isZero();
		assertThat(jdbc.queryForObject("SELECT status FROM repair_orders WHERE id = ?", String.class, orderId))
			.isEqualTo("IN_REPAIR");
		assertThat(dispatcher.runOnce()).isZero();
	}

	@Test
	void withdrawingConsentStopsPendingMessages() throws Exception {
		long orderId = orderInRepair(true);
		transition(orderId, "READY_FOR_PICKUP").andExpect(status().isOk());

		withdrawEmail(customerOf(orderId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.channels[0].channel").value("EMAIL"))
			.andExpect(jsonPath("$.channels[0].latest.granted").value(false));

		assertThat(outboxStatus(orderId)).isEqualTo("SKIPPED:CONSENT_WITHDRAWN");
		assertThat(dispatcher.runOnce()).isZero();
		assertThat(inbox().messages()).isEmpty();
	}

	@Test
	void theWorkerRevalidatesConsentJustBeforeSending() throws Exception {
		long orderId = orderInRepair(true);
		transition(orderId, "READY_FOR_PICKUP").andExpect(status().isOk());
		// A withdrawal that did not go through the use case (e.g. committed while the worker was
		// between claim and send): the worker's own check must catch it.
		jdbc.update("""
				INSERT INTO customer_consents (customer_id, channel, granted, source, stated_at, recorded_by, recorded_at)
				VALUES (?, 'EMAIL', FALSE, 'PHONE', now(), ?, now())""", customerOf(orderId), technicianId);

		assertThat(dispatcher.runOnce()).isEqualTo(1);

		assertThat(outboxStatus(orderId)).isEqualTo("SKIPPED:CONSENT_WITHDRAWN");
		assertThat(inbox().messages()).isEmpty();
		assertThat(jdbc.queryForList("SELECT outcome FROM notification_attempts", String.class)).containsExactly("SKIPPED");
	}

	@Test
	void transientFailuresAreRetriedWithABoundAndThenCanBeRetriedByHand() throws Exception {
		long orderId = orderInRepair(true);
		transition(orderId, "READY_FOR_PICKUP").andExpect(status().isOk());
		inbox().simulateFailures(100, false);

		dispatcher.runOnce();
		assertThat(outboxStatus(orderId)).isEqualTo("PENDING");
		assertThat(jdbc.queryForObject(
				"SELECT next_attempt_at > now() + interval '50 seconds' FROM notification_outbox", Boolean.class)).isTrue();
		// Not due yet: the next run leaves it alone.
		assertThat(dispatcher.runOnce()).isZero();
		for (int attempt = 2; attempt <= 5; attempt++) {
			jdbc.update("UPDATE notification_outbox SET next_attempt_at = now() - interval '1 second'");
			assertThat(dispatcher.runOnce()).isEqualTo(1);
		}

		assertThat(outboxStatus(orderId)).isEqualTo("FAILED");
		assertThat(jdbc.queryForList("SELECT outcome FROM notification_attempts ORDER BY id", String.class))
			.containsExactly("RETRY", "RETRY", "RETRY", "RETRY", "FAILED");
		assertThat(jdbc.queryForObject("SELECT last_error FROM notification_outbox", String.class))
			.isEqualTo("Simulated temporary failure");

		inbox().simulateFailures(0, false);
		long id = jdbc.queryForObject("SELECT id FROM notification_outbox", Long.class);
		mockMvc.perform(post("/api/v1/notifications/{id}/retry", id).session(manager).with(xsrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message.status").value("PENDING"))
			.andExpect(jsonPath("$.attempts.length()").value(5));
		assertThat(dispatcher.runOnce()).isEqualTo(1);
		assertThat(outboxStatus(orderId)).isEqualTo("SENT");
		// A sent message can never be retried (that would send it twice).
		mockMvc.perform(post("/api/v1/notifications/{id}/retry", id).session(manager).with(xsrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_RETRYABLE"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'NOTIFICATION_RETRY_REQUESTED'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void aPermanentRejectionFailsAtOnce() throws Exception {
		long orderId = orderInRepair(true);
		transition(orderId, "READY_FOR_PICKUP").andExpect(status().isOk());
		inbox().simulateFailures(1, true);

		dispatcher.runOnce();

		assertThat(outboxStatus(orderId)).isEqualTo("FAILED");
		assertThat(jdbc.queryForList("SELECT error_code FROM notification_attempts", String.class))
			.containsExactly("SIMULATED_REJECTED");
	}

	@Test
	void theSameEventIsEnqueuedOnce() throws Exception {
		long orderId = orderInRepair(true);
		NotificationRequest request = new NotificationRequest(EventType.REPAIR_READY_FOR_PICKUP, "test-1",
				customerOf(orderId), sanJose, SubjectType.REPAIR_ORDER, orderId, Map.of("orderCode", "OR-2026-000001"));
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		transaction.executeWithoutResult(status -> outbox.enqueue(request));
		transaction.executeWithoutResult(status -> outbox.enqueue(request));

		assertThat(countRows("notification_outbox")).isEqualTo(1);
	}

	@Test
	void aMessageWhoseWorkerDiedIsTakenOverAfterItsLease() throws Exception {
		long orderId = orderInRepair(true);
		transition(orderId, "READY_FOR_PICKUP").andExpect(status().isOk());
		// Claimed by a worker that never finished (its lease expired a minute ago).
		jdbc.update("""
				UPDATE notification_outbox SET status = 'SENDING', attempts = 1, locked_by = 'dead-worker',
				    locked_until = now() - interval '1 minute'""");

		assertThat(dispatcher.runOnce()).isEqualTo(1);

		assertThat(outboxStatus(orderId)).isEqualTo("SENT");
		assertThat(jdbc.queryForObject("SELECT attempts FROM notification_outbox", Integer.class)).isEqualTo(2);
	}

	@Test
	void concurrentWorkersNeverTakeTheSameMessage() throws Exception {
		long orderId = orderInRepair(true);
		long customerId = customerOf(orderId);
		for (int i = 0; i < 30; i++) {
			jdbc.update("""
					INSERT INTO notification_outbox (dedupe_key, event_type, channel, customer_id, branch_id, subject_type,
					    subject_id, payload, status, max_attempts, next_attempt_at, created_at, updated_at)
					VALUES (?, 'REPAIR_READY_FOR_PICKUP', 'EMAIL', ?, ?, 'REPAIR_ORDER', ?, '{"orderCode":"OR-2026-000001"}'::jsonb,
					    'PENDING', 5, now() - interval '1 second', now(), now())""", "load-" + i, customerId, sanJose, orderId);
		}

		ExecutorService pool = Executors.newFixedThreadPool(4);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> runs = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				Callable<Integer> work = () -> {
					start.await();
					int total = 0;
					int claimed;
					while ((claimed = dispatcher.runOnce()) > 0) {
						total += claimed;
					}
					return total;
				};
				runs.add(pool.submit(work));
			}
			start.countDown();
			int processed = 0;
			for (Future<Integer> run : runs) {
				processed += run.get(60, TimeUnit.SECONDS);
			}
			assertThat(processed).isEqualTo(30);
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(inbox().messages()).hasSize(30);
		assertThat(inbox().messages()).extracting(InboxMessage::messageId).doesNotHaveDuplicates();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE status = 'SENT' AND attempts = 1",
				Integer.class)).isEqualTo(30);
		assertThat(countRows("notification_attempts")).isEqualTo(30);
	}

	@Test
	void administrationIsScopedByBranchAndNeverShowsContent() throws Exception {
		long orderId = orderInRepair(true);
		transition(orderId, "READY_FOR_PICKUP").andExpect(status().isOk());
		dispatcher.runOnce();
		long id = jdbc.queryForObject("SELECT id FROM notification_outbox", Long.class);

		mockMvc.perform(get("/api/v1/notifications").session(manager))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].status").value("SENT"))
			.andExpect(jsonPath("$.content[0].reference").isString())
			.andExpect(jsonPath("$.content[0].recipientHint").value("l***@ejemplo.test"))
			.andExpect(jsonPath("$.content[0].body").doesNotExist());
		mockMvc.perform(get("/api/v1/notifications").session(alajuelaManager)).andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/v1/notifications/{id}", id).session(alajuelaManager)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/notifications").session(receptionist)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/notifications").session(technician)).andExpect(status().isForbidden());
		// The development inbox (full content) is for the administrator only.
		mockMvc.perform(get("/api/v1/notifications/dev-inbox").session(manager)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/notifications/dev-inbox").session(loginAsAdmin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].to").value("lucia@ejemplo.test"));
	}

	private org.springframework.test.web.servlet.RequestBuilder put(long orderId, String diagnosis) {
		String version = jdbc.queryForObject("SELECT version FROM repair_orders WHERE id = ?", String.class, orderId);
		return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.put("/api/v1/repair-orders/{id}/diagnosis", orderId)
			.session(technician)
			.with(xsrf())
			.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
			.content("{\"diagnosis\":\"%s\",\"version\":%s}".formatted(diagnosis, version));
	}

}
