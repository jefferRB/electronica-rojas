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

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import dev.jeffrojas.electronicarojas.BrowserClient;
import dev.jeffrojas.electronicarojas.BrowserClient.Response;

/**
 * BR-REP-013 under real concurrency (embedded Tomcat, one HTTP session per thread, PostgreSQL):
 * technicians competing for the last units, double clicks, parallel corrections, and consumption
 * mixed with transfers of the same product (lock order).
 */
class RepairPartConcurrencyIntegrationTests extends RepairPartTestSupport {

	private static final int THREADS = 6;

	@LocalServerPort
	private int port;

	private final List<BrowserClient> clients = new ArrayList<>();

	@AfterEach
	void closeClients() {
		clients.forEach(BrowserClient::close);
		clients.clear();
	}

	private void openClients(String email) throws Exception {
		for (int i = 0; i < THREADS; i++) {
			clients.add(BrowserClient.login(port, email, USER_PASSWORD));
		}
	}

	@Test
	void techniciansCompetingForTheLastUnitsNeverOverdrawStock() throws Exception {
		long part = createProduct("CORREA-01");
		setStock(sanJose, part, 2);
		List<Long> orders = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			orders.add(orderInRepair());
		}
		openClients("tecnico@electronica-rojas.test");

		List<Response> responses = runConcurrently(index -> () -> clients.get(index)
			.postJson("/api/v1/repair-orders/" + orders.get(index) + "/parts", consumeJson(UUID.randomUUID(), part, 1)));

		assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(2);
		assertThat(responses).filteredOn(r -> r.status() == 409)
			.hasSize(THREADS - 2)
			.allSatisfy(r -> assertThat((String) JsonPath.read(r.body(), "$.code")).isEqualTo("INSUFFICIENT_STOCK"));
		assertThat(stockOf(sanJose, part)).isZero();
		assertThat(countRows("repair_part_usages")).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE type = 'OUT_FOR_REPAIR'",
				Integer.class)).isEqualTo(2);
	}

	@Test
	void concurrentDuplicatesOfOneConsumptionApplyOnce() throws Exception {
		long part = createProduct("CORREA-01");
		setStock(sanJose, part, 5);
		long orderId = orderInRepair();
		openClients("tecnico@electronica-rojas.test");
		String json = consumeJson(UUID.randomUUID(), part, 2);

		List<Response> responses = runConcurrently(index -> () -> clients.get(index)
			.postJson("/api/v1/repair-orders/" + orderId + "/parts", json));

		// The first one records the line; the others wait for the order lock and replay it.
		assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(1);
		assertThat(responses).filteredOn(r -> r.status() == 200).hasSize(THREADS - 1);
		assertThat(stockOf(sanJose, part)).isEqualTo(3);
		assertThat(countRows("repair_part_usages")).isEqualTo(1);
		assertThat(movementsOfOrder(orderId)).isEqualTo(1);
	}

	@Test
	void parallelCorrectionsNeverReturnMoreThanWasConsumed() throws Exception {
		long part = createProduct("CORREA-01");
		setStock(sanJose, part, 5);
		long orderId = orderInRepair();
		consume(technician, orderId, UUID.randomUUID(), part, 3);
		long usageId = usageIdOf(orderId, part);
		openClients("gerente@electronica-rojas.test");

		List<Response> responses = runConcurrently(index -> () -> clients.get(index).postJson(
				"/api/v1/repair-orders/" + orderId + "/parts/" + usageId + "/returns",
				returnJson(UUID.randomUUID(), 1, "Sobrante")));

		assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(3);
		assertThat(responses).filteredOn(r -> r.status() == 409).hasSize(THREADS - 3);
		assertThat(stockOf(sanJose, part)).isEqualTo(5);
		assertThat(jdbc.queryForObject("SELECT returned_quantity FROM repair_part_usages WHERE id = ?", Integer.class,
				usageId)).isEqualTo(3);
		assertThat(countRows("repair_part_returns")).isEqualTo(3);
	}

	/**
	 * Consumption (order row, then stock row) running together with transfers in both directions
	 * (stock rows in branch order) of the same product: every request ends with a business answer,
	 * no deadlock, and the units add up.
	 */
	@Test
	void consumptionAndTransfersOfTheSameProductDoNotDeadlock() throws Exception {
		long part = createProduct("CORREA-01");
		setStock(sanJose, part, 20);
		setStock(alajuela, part, 20);
		createUser("admin.stock@electronica-rojas.test", dev.jeffrojas.electronicarojas.security.Role.ADMIN);
		List<Long> orders = new ArrayList<>();
		for (int i = 0; i < THREADS / 2; i++) {
			orders.add(orderInRepair());
		}
		List<BrowserClient> technicians = new ArrayList<>();
		for (int i = 0; i < THREADS / 2; i++) {
			technicians.add(BrowserClient.login(port, "tecnico@electronica-rojas.test", USER_PASSWORD));
			clients.add(BrowserClient.login(port, "admin.stock@electronica-rojas.test", USER_PASSWORD));
		}
		clients.addAll(technicians);

		List<Response> responses = runConcurrently(index -> {
			BrowserClient client = clients.get(index);
			if (index < THREADS / 2) {
				boolean toAlajuela = index % 2 == 0;
				return () -> client.postJson("/api/v1/stock-transfers", """
						{"operationId":"%s","sourceBranchId":%d,"destinationBranchId":%d,"productId":%d,"quantity":2}
						""".formatted(UUID.randomUUID(), toAlajuela ? sanJose : alajuela, toAlajuela ? alajuela : sanJose,
						part));
			}
			long orderId = orders.get(index - THREADS / 2);
			return () -> client.postJson("/api/v1/repair-orders/" + orderId + "/parts",
					consumeJson(UUID.randomUUID(), part, 1));
		});

		assertThat(responses).allSatisfy(r -> assertThat(r.status()).isEqualTo(201));
		assertThat(stockOf(sanJose, part) + stockOf(alajuela, part)).isEqualTo(40 - THREADS / 2);
		assertThat(countRows("repair_part_usages")).isEqualTo(THREADS / 2);
	}

	@FunctionalInterface
	private interface Task {

		Callable<Response> forClient(int index);

	}

	private List<Response> runConcurrently(Task task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(clients.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Response>> futures = new ArrayList<>();
			for (int i = 0; i < clients.size(); i++) {
				Callable<Response> call = task.forClient(i);
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
