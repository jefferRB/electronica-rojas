package dev.jeffrojas.electronicarojas.servicerequests;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockHttpSession;

import dev.jeffrojas.electronicarojas.BrowserClient;
import dev.jeffrojas.electronicarojas.BrowserClient.Response;
import dev.jeffrojas.electronicarojas.security.Role;

/**
 * BR-SRV-006 under real concurrency: several managers confirm different requests for the same
 * technician and the same time, released at the same instant over real HTTP sessions. Exactly
 * one may win; the others get a clear SCHEDULE_CONFLICT and nothing partial is stored.
 */
class VisitConcurrencyIntegrationTests extends HomeServiceTestSupport {

	private static final int THREADS = 6;

	@LocalServerPort
	private int port;

	private long technicianId;

	private final List<Long> requestIds = new ArrayList<>();

	private final List<BrowserClient> clients = new ArrayList<>();

	private LocalDate monday;

	@BeforeEach
	void setUp() throws Exception {
		long sanJose = createBranch("SJ-01");
		technicianId = createUser("tecnico@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		weekdayShifts(technicianId, sanJose);
		for (int i = 0; i < THREADS; i++) {
			createUser("gerente" + i + "@electronica-rojas.test", Role.BRANCH_MANAGER, sanJose);
		}
		MockHttpSession creator = login("gerente0@electronica-rojas.test");
		for (int i = 0; i < THREADS; i++) {
			requestIds.add(staffRequest(creator, sanJose, "Cliente Concurrencia " + i, "8100-000" + i));
			clients.add(BrowserClient.login(port, "gerente" + i + "@electronica-rojas.test", USER_PASSWORD));
		}
		monday = nextMonday();
	}

	@AfterEach
	void closeClients() {
		clients.forEach(BrowserClient::close);
		clients.clear();
	}

	@Test
	void onlyOneOfManySimultaneousConfirmationsGetsTheSlot() throws Exception {
		List<Response> responses = runConcurrently(index -> () -> clients.get(index)
			.postJson("/api/v1/service-requests/" + requestIds.get(index) + "/visits", """
					{"operationId":"%s","technicianId":%d,"start":"%s","durationMinutes":90,"confirm":true}
					""".formatted(UUID.randomUUID(), technicianId, at(monday, "09:00"))));

		assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(1);
		assertThat(responses).filteredOn(r -> r.status() == 409)
			.hasSize(THREADS - 1)
			.allSatisfy(r -> assertThat(r.body()).contains("SCHEDULE_CONFLICT"));
		assertThat(countRows("service_visits")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM service_requests WHERE status = 'ACCEPTED'", Integer.class))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM service_request_events WHERE event_type = 'VISIT_CONFIRMED'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void overlappingButDifferentSlotsAreAlsoSerialized() throws Exception {
		String[] starts = { "09:00", "09:30", "10:00", "10:30", "13:00", "13:30" };
		List<Response> responses = runConcurrently(index -> () -> clients.get(index)
			.postJson("/api/v1/service-requests/" + requestIds.get(index) + "/visits", """
					{"operationId":"%s","technicianId":%d,"start":"%s","durationMinutes":90,"confirm":true}
					""".formatted(UUID.randomUUID(), technicianId, at(monday, starts[index]))));

		assertThat(responses).allSatisfy(r -> assertThat(r.status()).isIn(201, 409));
		// Whatever the order, the confirmed visits never overlap (blocked ranges included).
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM service_visits a JOIN service_visits b ON a.id < b.id
				WHERE a.technician_id = b.technician_id
				  AND tstzrange(a.scheduled_start, a.blocked_until) && tstzrange(b.scheduled_start, b.blocked_until)""",
				Integer.class)).isZero();
		assertThat(countRows("service_visits")).isEqualTo(responses.stream().filter(r -> r.status() == 201).count());
		assertThat(responses).filteredOn(r -> r.status() == 201).hasSizeBetween(2, 4);
	}

	@FunctionalInterface
	private interface Task {

		Callable<Response> forIndex(int index);

	}

	private List<Response> runConcurrently(Task task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Response>> futures = new ArrayList<>();
			for (int i = 0; i < THREADS; i++) {
				Callable<Response> call = task.forIndex(i);
				futures.add(pool.submit(() -> {
					start.await();
					return call.call();
				}));
			}
			start.countDown();
			List<Response> responses = new ArrayList<>();
			for (Future<Response> future : futures) {
				responses.add(future.get(60, TimeUnit.SECONDS));
			}
			return responses;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
