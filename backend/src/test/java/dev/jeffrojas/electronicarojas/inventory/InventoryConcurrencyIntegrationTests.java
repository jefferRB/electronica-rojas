package dev.jeffrojas.electronicarojas.inventory;

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
 * E2E-06 / BR-TRF-004 / DATA-004: real concurrent HTTP requests (one session per thread, all
 * released at the same instant) against PostgreSQL 17. Verifies that stock never goes negative,
 * that nothing is applied partially, that opposite transfers do not deadlock and that concurrent
 * duplicates are applied once.
 */
class InventoryConcurrencyIntegrationTests extends IntegrationTestSupport {

	private static final int THREADS = 8;

	@LocalServerPort
	private int port;

	private long sanJose;

	private long alajuela;

	private long product;

	private final List<BrowserClient> clients = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		sanJose = createBranch("SJ-01");
		alajuela = createBranch("AL-01");
		product = createProduct("CMP-100");
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, sanJose, alajuela);
		for (int i = 0; i < THREADS; i++) {
			clients.add(BrowserClient.login(port, "manager@electronica-rojas.test", USER_PASSWORD));
		}
	}

	@AfterEach
	void closeClients() {
		clients.forEach(BrowserClient::close);
		clients.clear();
	}

	/** Two (here eight) users try to take the last units at the same time. */
	@Test
	void concurrentIssuesNeverOversellTheLastUnits() throws Exception {
		setStock(sanJose, product, 3);

		List<Response> responses = runConcurrently(client -> () -> client.postJson("/api/v1/stock-movements", """
				{"operationId":"%s","branchId":%d,"productId":%d,"type":"ISSUE","quantity":1,"reason":"Venta"}
				""".formatted(UUID.randomUUID(), sanJose, product)));

		assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(3);
		assertThat(responses).filteredOn(r -> r.status() == 409).hasSize(THREADS - 3);
		assertThat(stockOf(sanJose, product)).isZero();
		assertThat(countRows("stock_movements")).isEqualTo(3);
		// Each success saw a different balance: the updates were serialized, none was lost.
		assertThat(jdbc.queryForList("SELECT balance_after FROM stock_movements ORDER BY balance_after", Integer.class))
			.containsExactly(0, 1, 2);
		assertLedgerMatchesStock(sanJose, 3);
	}

	@Test
	void concurrentTransfersNeverConsumeMoreThanAvailable() throws Exception {
		setStock(sanJose, product, 5);

		List<Response> responses = runConcurrently(client -> () -> client.postJson("/api/v1/stock-transfers",
				transferJson(UUID.randomUUID(), sanJose, alajuela, 2)));

		assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(2);
		assertThat(responses).filteredOn(r -> r.status() == 409).hasSize(THREADS - 2);
		assertThat(stockOf(sanJose, product)).isEqualTo(1);
		assertThat(stockOf(alajuela, product)).isEqualTo(4);
		assertNoPartialTransfers();
		assertLedgerMatchesStock(sanJose, 5);
		assertLedgerMatchesStock(alajuela, 0);
	}

	/**
	 * Half the threads move A->B while the other half move B->A, several times. Without a global
	 * lock order this is the textbook deadlock; PostgreSQL would abort one of them (409 here).
	 */
	@Test
	void oppositeTransfersDoNotDeadlock() throws Exception {
		setStock(sanJose, product, 20);
		setStock(alajuela, product, 20);
		int rounds = 3;
		List<BrowserClient> snapshot = List.copyOf(clients);

		List<Response> responses = runConcurrently(client -> () -> {
			boolean forward = snapshot.indexOf(client) % 2 == 0;
			Response last = null;
			for (int i = 0; i < rounds; i++) {
				last = client.postJson("/api/v1/stock-transfers", forward
						? transferJson(UUID.randomUUID(), sanJose, alajuela, 1)
						: transferJson(UUID.randomUUID(), alajuela, sanJose, 1));
				if (last.status() != 201) {
					return last;
				}
			}
			return last;
		});

		assertThat(responses).allSatisfy(r -> assertThat(r.status()).as(r.body()).isEqualTo(201));
		assertThat(countRows("stock_transfers")).isEqualTo(THREADS * rounds);
		assertThat(stockOf(sanJose, product)).isEqualTo(20);
		assertThat(stockOf(alajuela, product)).isEqualTo(20);
		assertNoPartialTransfers();
	}

	/** A double click that reaches the server twice at the same time is still applied once. */
	@Test
	void concurrentDuplicatesOfOneOperationAreAppliedOnce() throws Exception {
		setStock(sanJose, product, 10);
		UUID operation = UUID.randomUUID();
		// Idempotency is per actor, so every thread is the same user in its own session.
		List<Response> responses = runConcurrently(
				client -> () -> client.postJson("/api/v1/stock-transfers", transferJson(operation, sanJose, alajuela, 3)));

		assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(1);
		assertThat(responses).filteredOn(r -> r.status() == 200).hasSize(THREADS - 1);
		assertThat(responses).extracting(r -> (Integer) JsonPath.read(r.body(), "$.id")).containsOnly(
				jdbc.queryForObject("SELECT id FROM stock_transfers", Integer.class));
		assertThat(stockOf(sanJose, product)).isEqualTo(7);
		assertThat(stockOf(alajuela, product)).isEqualTo(3);
		assertThat(countRows("stock_movements")).isEqualTo(2);
	}

	private String transferJson(UUID operationId, long source, long destination, int quantity) {
		return """
				{"operationId":"%s","sourceBranchId":%d,"destinationBranchId":%d,"productId":%d,"quantity":%d}
				""".formatted(operationId, source, destination, product, quantity);
	}

	private interface Task {

		Callable<Response> forClient(BrowserClient client);

	}

	/** Starts one task per client and releases them together. */
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

	/** Every transfer header has exactly its two movements: nothing applied partially. */
	private void assertNoPartialTransfers() {
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM stock_transfers t
				WHERE (SELECT count(*) FROM stock_movements m WHERE m.transfer_id = t.id) <> 2
				""", Integer.class)).isZero();
		assertThat(countRows("stock_movements")).isEqualTo(2 * countRows("stock_transfers"));
	}

	/** Initial stock + sum of all movement deltas == current stock, and it is never negative. */
	private void assertLedgerMatchesStock(long branchId, int initial) {
		Integer delta = jdbc.queryForObject(
				"SELECT coalesce(sum(quantity_delta), 0) FROM stock_movements WHERE branch_id = ? AND product_id = ?",
				Integer.class, branchId, product);
		assertThat(stockOf(branchId, product)).isEqualTo(initial + delta).isNotNegative();
	}

}
