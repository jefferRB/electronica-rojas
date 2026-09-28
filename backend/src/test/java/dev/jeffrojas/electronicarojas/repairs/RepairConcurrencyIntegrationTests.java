package dev.jeffrojas.electronicarojas.repairs;

import static org.assertj.core.api.Assertions.assertThat;

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

import dev.jeffrojas.electronicarojas.BrowserClient;
import dev.jeffrojas.electronicarojas.BrowserClient.Response;
import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;
import com.jayway.jsonpath.JsonPath;

/**
 * BR-REP-005 under real concurrency: several people at the counter hand over the same appliance at
 * the same instant, or the same reception is submitted many times at once. The order row lock and
 * the unique operation id must let exactly one of them win.
 */
class RepairConcurrencyIntegrationTests extends IntegrationTestSupport {

	private static final int THREADS = 6;

	@LocalServerPort
	private int port;

	private long sanJose;

	private final List<BrowserClient> clients = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		sanJose = createBranch("SJ-01");
		createUser("recepcion@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		for (int i = 0; i < THREADS; i++) {
			clients.add(BrowserClient.login(port, "recepcion@electronica-rojas.test", USER_PASSWORD));
		}
	}

	@AfterEach
	void closeClients() {
		clients.forEach(BrowserClient::close);
		clients.clear();
	}

	private String reception(UUID operationId) {
		return """
				{"operationId":"%s","branchId":%d,
				 "newCustomer":{"fullName":"Cliente Prueba","phone":"8888-7777"},
				 "deviceType":"Lavadora","brand":"Whirlpool","reportedFault":"No centrifuga","physicalCondition":"Bueno"}
				""".formatted(operationId, sanJose);
	}

	@Test
	void anApplianceIsDeliveredExactlyOnce() throws Exception {
		Response created = clients.get(0).postJson("/api/v1/repair-orders",
				reception(UUID.randomUUID()));
		assertThat(created.status()).isEqualTo(201);
		long orderId = ((Number) JsonPath.read(created.body(), "$.id")).longValue();
		// Cancelled at the counter: the next (and only) step is handing the appliance back.
		assertThat(clients.get(0).postJson("/api/v1/repair-orders/" + orderId + "/status",
				"{\"toStatus\":\"CANCELLED\",\"reason\":\"El cliente desistio\"}").status()).isEqualTo(200);

		List<Response> responses = runConcurrently(client -> () -> client
			.postJson("/api/v1/repair-orders/" + orderId + "/status", "{\"toStatus\":\"DELIVERED\"}"));

		assertThat(responses).filteredOn(r -> r.status() == 200).hasSize(1);
		assertThat(responses).filteredOn(r -> r.status() == 409).hasSize(THREADS - 1);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM repair_status_history WHERE order_id = ? AND to_status = 'DELIVERED'",
				Integer.class, orderId)).isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM audit_events WHERE action = 'REPAIR_STATUS_CHANGED' AND details->>'toStatus' = 'DELIVERED'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void concurrentDuplicatesOfOneReceptionCreateOneOrder() throws Exception {
		String json = reception(UUID.randomUUID());

		List<Response> responses = runConcurrently(client -> () -> client.postJson("/api/v1/repair-orders", json));

		// Duplicates wait for the first reception (advisory lock on the operationId) and replay it.
		assertThat(responses).allSatisfy(r -> assertThat(r.status()).isEqualTo(201));
		assertThat(responses).extracting(r -> ((Number) JsonPath.read(r.body(), "$.id")).longValue())
			.containsOnly(jdbc.queryForObject("SELECT id FROM repair_orders", Long.class));
		assertThat(countRows("repair_orders")).isEqualTo(1);
		assertThat(countRows("customers")).isEqualTo(1);
		assertThat(countRows("repair_status_history")).isEqualTo(1);
	}

	@FunctionalInterface
	private interface Task {

		Callable<Response> forClient(BrowserClient client);

	}

	private List<Response> runConcurrently(Task task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(clients.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Response>> futures = new ArrayList<>();
			for (BrowserClient client : clients) {
				Callable<Response> call = task.forClient(client);
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
