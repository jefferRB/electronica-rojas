package dev.jeffrojas.electronicarojas.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import dev.jeffrojas.electronicarojas.BrowserClient;
import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;
import com.jayway.jsonpath.JsonPath;

/**
 * TST-004 / E2E-03..05: happy path, insufficient stock, same branch, wrong role, scope,
 * idempotency and rollback after a failure in the middle of the transaction.
 * Concurrency is covered in InventoryConcurrencyIntegrationTests.
 */
class StockTransferIntegrationTests extends IntegrationTestSupport {

	@LocalServerPort
	private int port;

	private long sanJose;

	private long alajuela;

	private long heredia;

	private long product;

	private MockHttpSession manager;

	@BeforeEach
	void setUp() throws Exception {
		sanJose = createBranch("SJ-01");
		alajuela = createBranch("AL-01");
		heredia = createBranch("HE-01");
		product = createProduct("CMP-100");
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, sanJose, alajuela);
		manager = login("manager@electronica-rojas.test");
	}

	private ResultActions transfer(MockHttpSession session, UUID operationId, long source, long destination,
			int quantity) throws Exception {
		String body = """
				{"operationId":"%s","sourceBranchId":%d,"destinationBranchId":%d,"productId":%d,"quantity":%d,"reason":"Reposición"}
				""".formatted(operationId, source, destination, product, quantity);
		return mockMvc.perform(post("/api/v1/stock-transfers").session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	/** E2E-03: 10/2 -> transfer 4 -> 6/6, two movements and audit in one transaction. */
	@Test
	void transferMovesUnitsAndRecordsBothMovementsAndAudit() throws Exception {
		setStock(sanJose, product, 10);
		setStock(alajuela, product, 2);
		UUID operation = UUID.randomUUID();

		String body = transfer(manager, operation, sanJose, alajuela, 4).andExpect(status().isCreated())
			.andExpect(jsonPath("$.sourceBalanceAfter").value(6))
			.andExpect(jsonPath("$.destinationBalanceAfter").value(6))
			.andExpect(jsonPath("$.source.code").value("SJ-01"))
			.andExpect(jsonPath("$.destination.code").value("AL-01"))
			.andExpect(jsonPath("$.actor.fullName").value("Usuario BRANCH_MANAGER"))
			.andExpect(jsonPath("$.movements[*].type").value(contains("TRANSFER_OUT", "TRANSFER_IN")))
			.andExpect(jsonPath("$.movements[*].quantityDelta").value(contains(-4, 4)))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(stockOf(sanJose, product)).isEqualTo(6);
		assertThat(stockOf(alajuela, product)).isEqualTo(6);
		assertThat(countRows("stock_transfers")).isEqualTo(1);
		assertThat(countRows("stock_movements")).isEqualTo(2);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM audit_events WHERE action = 'STOCK_TRANSFER_COMPLETED' AND operation_id = ?",
				Integer.class, operation)).isEqualTo(2);

		Integer transferId = JsonPath.read(body, "$.id");
		mockMvc.perform(get("/api/v1/stock-transfers/{id}", transferId).session(manager))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.movements.length()").value(2))
			.andExpect(jsonPath("$.sourceBalanceAfter").value(6));
	}

	@Test
	void destinationRowIsCreatedWhenTheBranchNeverHadTheProduct() throws Exception {
		setStock(sanJose, product, 3);

		transfer(manager, UUID.randomUUID(), sanJose, alajuela, 3).andExpect(status().isCreated())
			.andExpect(jsonPath("$.destinationBalanceAfter").value(3));

		assertThat(stockOf(sanJose, product)).isZero();
		assertThat(stockOf(alajuela, product)).isEqualTo(3);
	}

	/** E2E-04: 2 available, 4 requested -> 409 with the balance, zero changes. */
	@Test
	void insufficientStockRejectsTheWholeTransfer() throws Exception {
		setStock(sanJose, product, 2);
		setStock(alajuela, product, 1);

		transfer(manager, UUID.randomUUID(), sanJose, alajuela, 4).andExpect(status().isConflict())
			.andExpect(jsonPath("$.available").value(2))
			.andExpect(jsonPath("$.branchId").value(sanJose));

		assertThat(stockOf(sanJose, product)).isEqualTo(2);
		assertThat(stockOf(alajuela, product)).isEqualTo(1);
		assertThat(countRows("stock_transfers") + countRows("stock_movements") + countRows("audit_events")).isZero();
	}

	@Test
	void sameBranchAndNonPositiveQuantitiesAreRejected() throws Exception {
		setStock(sanJose, product, 5);

		transfer(manager, UUID.randomUUID(), sanJose, sanJose, 1).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("destinationBranchId"));
		transfer(manager, UUID.randomUUID(), sanJose, alajuela, 0).andExpect(status().isBadRequest());

		assertThat(stockOf(sanJose, product)).isEqualTo(5);
	}

	/** BR-TRF-001: the manager must be authorized on BOTH branches. */
	@Test
	void managerNeedsAccessToSourceAndDestination() throws Exception {
		setStock(sanJose, product, 5);
		setStock(heredia, product, 5);

		transfer(manager, UUID.randomUUID(), sanJose, heredia, 1).andExpect(status().isNotFound());
		transfer(manager, UUID.randomUUID(), heredia, sanJose, 1).andExpect(status().isNotFound());

		assertThat(stockOf(sanJose, product)).isEqualTo(5);
		assertThat(stockOf(heredia, product)).isEqualTo(5);
	}

	@Test
	void receptionistsAndTechniciansCannotTransfer() throws Exception {
		setStock(sanJose, product, 5);
		createUser("recep@electronica-rojas.test", Role.RECEPTIONIST, sanJose, alajuela);
		createUser("tech@electronica-rojas.test", Role.TECHNICIAN, sanJose, alajuela);

		transfer(login("recep@electronica-rojas.test"), UUID.randomUUID(), sanJose, alajuela, 1)
			.andExpect(status().isForbidden());
		transfer(login("tech@electronica-rojas.test"), UUID.randomUUID(), sanJose, alajuela, 1)
			.andExpect(status().isForbidden());
		assertThat(stockOf(sanJose, product)).isEqualTo(5);
	}

	@Test
	void inactiveDestinationOrProductBlocksTheTransfer() throws Exception {
		setStock(sanJose, product, 5);
		MockHttpSession admin = loginAsAdmin();
		jdbc.update("UPDATE branches SET active = FALSE WHERE id = ?", heredia);
		transfer(admin, UUID.randomUUID(), sanJose, heredia, 1).andExpect(status().isNotFound());

		jdbc.update("UPDATE products SET active = FALSE WHERE id = ?", product);
		transfer(admin, UUID.randomUUID(), sanJose, alajuela, 1).andExpect(status().isConflict());

		assertThat(stockOf(sanJose, product)).isEqualTo(5);
	}

	/** E2E-05: double click / network retry with the same operationId. */
	@Test
	void replayingAnOperationReturnsTheSameResultWithoutApplyingItAgain() throws Exception {
		setStock(sanJose, product, 10);
		UUID operation = UUID.randomUUID();

		String first = transfer(manager, operation, sanJose, alajuela, 4).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String second = transfer(manager, operation, sanJose, alajuela, 4).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(second).isEqualTo(first);
		assertThat(stockOf(sanJose, product)).isEqualTo(6);
		assertThat(stockOf(alajuela, product)).isEqualTo(4);
		assertThat(countRows("stock_transfers")).isEqualTo(1);
		assertThat(countRows("stock_movements")).isEqualTo(2);
	}

	@Test
	void sameOperationIdWithADifferentPayloadOrActorIsAConflict() throws Exception {
		setStock(sanJose, product, 10);
		UUID operation = UUID.randomUUID();
		transfer(manager, operation, sanJose, alajuela, 4).andExpect(status().isCreated());

		transfer(manager, operation, sanJose, alajuela, 5).andExpect(status().isConflict());
		transfer(manager, operation, alajuela, sanJose, 4).andExpect(status().isConflict());
		transfer(loginAsAdmin(), operation, sanJose, alajuela, 4).andExpect(status().isConflict());

		assertThat(stockOf(sanJose, product)).isEqualTo(6);
	}

	@Test
	void transferDetailIsHiddenFromManagersOfOtherBranches() throws Exception {
		setStock(sanJose, product, 10);
		String body = transfer(manager, UUID.randomUUID(), sanJose, alajuela, 1).andReturn().getResponse()
			.getContentAsString();
		Integer transferId = JsonPath.read(body, "$.id");
		createUser("other@electronica-rojas.test", Role.BRANCH_MANAGER, heredia);
		createUser("recep@electronica-rojas.test", Role.RECEPTIONIST, sanJose);

		mockMvc.perform(get("/api/v1/stock-transfers/{id}", transferId).session(login("other@electronica-rojas.test")))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/stock-transfers/{id}", transferId + 100).session(login("other@electronica-rojas.test")))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/stock-transfers/{id}", transferId).session(login("recep@electronica-rojas.test")))
			.andExpect(status().isForbidden());
	}

	/**
	 * TST-004 "rollback after a simulated failure": a test-only trigger makes the INSERT of the
	 * TRANSFER_IN movement fail AFTER the source was debited, the destination credited and the
	 * header and TRANSFER_OUT written. Nothing of it may survive.
	 */
	@Test
	void aFailureInTheMiddleRollsBackEverything() throws Exception {
		setStock(sanJose, product, 10);
		setStock(alajuela, product, 2);
		jdbc.execute("""
				CREATE FUNCTION test_fail_transfer_in() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN RAISE EXCEPTION 'simulated failure'; END; $$
				""");
		jdbc.execute("""
				CREATE TRIGGER test_fail_transfer_in BEFORE INSERT ON stock_movements
				FOR EACH ROW WHEN (NEW.type = 'TRANSFER_IN') EXECUTE FUNCTION test_fail_transfer_in()
				""");
		try (BrowserClient browser = BrowserClient.login(port, "manager@electronica-rojas.test", USER_PASSWORD)) {
			// Real HTTP: MockMvc would rethrow the exception instead of producing the 500.
			BrowserClient.Response response = browser.postJson("/api/v1/stock-transfers", """
					{"operationId":"%s","sourceBranchId":%d,"destinationBranchId":%d,"productId":%d,"quantity":4}
					""".formatted(UUID.randomUUID(), sanJose, alajuela, product));
			assertThat(response.status()).isEqualTo(500);
			assertThat(response.body()).doesNotContain("simulated failure", "Exception", "trace");
		}
		finally {
			jdbc.execute("DROP TRIGGER test_fail_transfer_in ON stock_movements");
			jdbc.execute("DROP FUNCTION test_fail_transfer_in()");
		}

		assertThat(stockOf(sanJose, product)).isEqualTo(10);
		assertThat(stockOf(alajuela, product)).isEqualTo(2);
		assertThat(countRows("stock_transfers") + countRows("stock_movements") + countRows("audit_events")).isZero();
	}

}
