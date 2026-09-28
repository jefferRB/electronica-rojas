package dev.jeffrojas.electronicarojas.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;

/** FR-INV-001/004, BR-INV-002..005: receipts, issues, adjustments, stock views and scope. */
class StockMovementIntegrationTests extends IntegrationTestSupport {

	private long sanJose;

	private long alajuela;

	private long product;

	private MockHttpSession manager;

	@BeforeEach
	void setUp() throws Exception {
		sanJose = createBranch("SJ-01");
		alajuela = createBranch("AL-01");
		product = createProduct("CMP-100");
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, sanJose);
		manager = login("manager@electronica-rojas.test");
	}

	private ResultActions record(MockHttpSession session, UUID operationId, long branchId, String type, int quantity,
			String reason) throws Exception {
		String body = """
				{"operationId":"%s","branchId":%d,"productId":%d,"type":"%s","quantity":%d,"reason":"%s"}
				""".formatted(operationId, branchId, product, type, quantity, reason);
		return mockMvc.perform(
				post("/api/v1/stock-movements").session(session).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void firstReceiptCreatesTheStockRowAndAMovementWithBalances() throws Exception {
		UUID operation = UUID.randomUUID();

		record(manager, operation, sanJose, "RECEIPT", 10, "Compra factura 123").andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("RECEIPT"))
			.andExpect(jsonPath("$.quantityDelta").value(10))
			.andExpect(jsonPath("$.balanceBefore").value(0))
			.andExpect(jsonPath("$.balanceAfter").value(10))
			.andExpect(jsonPath("$.actor.fullName").value("Usuario BRANCH_MANAGER"))
			.andExpect(jsonPath("$.operationId").value(operation.toString()));

		assertThat(stockOf(sanJose, product)).isEqualTo(10);
		assertThat(countRows("stock_movements")).isEqualTo(1);
	}

	@Test
	void issuesAndAdjustmentsChangeStockWithTheRightSign() throws Exception {
		setStock(sanJose, product, 10);

		record(manager, UUID.randomUUID(), sanJose, "ISSUE", 3, "Venta mostrador").andExpect(status().isCreated())
			.andExpect(jsonPath("$.quantityDelta").value(-3))
			.andExpect(jsonPath("$.balanceAfter").value(7));
		record(manager, UUID.randomUUID(), sanJose, "ADJUSTMENT_OUT", 2, "Conteo físico").andExpect(status().isCreated())
			.andExpect(jsonPath("$.balanceAfter").value(5));
		record(manager, UUID.randomUUID(), sanJose, "ADJUSTMENT_IN", 1, "Conteo físico").andExpect(status().isCreated())
			.andExpect(jsonPath("$.balanceAfter").value(6));

		assertThat(stockOf(sanJose, product)).isEqualTo(6);
	}

	@Test
	void insufficientStockIsA409WithTheAvailableQuantityAndChangesNothing() throws Exception {
		setStock(sanJose, product, 2);

		record(manager, UUID.randomUUID(), sanJose, "ISSUE", 4, "Venta").andExpect(status().isConflict())
			.andExpect(jsonPath("$.available").value(2))
			.andExpect(jsonPath("$.requested").value(4));

		assertThat(stockOf(sanJose, product)).isEqualTo(2);
		assertThat(countRows("stock_movements")).isZero();
		assertThat(countRows("audit_events")).isZero();
	}

	@Test
	void zeroNegativeMissingReasonAndTransferTypesAreRejected() throws Exception {
		record(manager, UUID.randomUUID(), sanJose, "RECEIPT", 0, "x").andExpect(status().isBadRequest());
		record(manager, UUID.randomUUID(), sanJose, "RECEIPT", -5, "x").andExpect(status().isBadRequest());
		record(manager, UUID.randomUUID(), sanJose, "RECEIPT", 1, " ").andExpect(status().isBadRequest());
		record(manager, UUID.randomUUID(), sanJose, "TRANSFER_IN", 1, "x").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("type"));

		assertThat(countRows("stock_movements")).isZero();
	}

	@Test
	void inactiveProductsAndBranchesCannotReceiveNewMovements() throws Exception {
		long inactiveProduct = createProduct("OLD-1", false);
		String body = """
				{"operationId":"%s","branchId":%d,"productId":%d,"type":"RECEIPT","quantity":1,"reason":"x"}
				""".formatted(UUID.randomUUID(), sanJose, inactiveProduct);
		mockMvc.perform(post("/api/v1/stock-movements").session(manager).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isConflict());

		jdbc.update("UPDATE branches SET active = FALSE WHERE id = ?", sanJose);
		record(loginAsAdmin(), UUID.randomUUID(), sanJose, "RECEIPT", 1, "x").andExpect(status().isNotFound());
	}

	/** IDOR: changing the branch id in the body or URL never reaches another branch's stock. */
	@Test
	void foreignBranchStockIsIndistinguishableFromMissing() throws Exception {
		setStock(alajuela, product, 50);

		record(manager, UUID.randomUUID(), alajuela, "ISSUE", 1, "x").andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/branches/{id}/stock", alajuela).session(manager)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/branches/{id}/movements", alajuela).session(manager))
			.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/v1/branches/{id}/stock/{p}/minimum", alajuela, product).session(manager)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"minimumQuantity\":5}")).andExpect(status().isNotFound());

		assertThat(stockOf(alajuela, product)).isEqualTo(50);
	}

	@Test
	void receptionistsReadStockButCannotMoveIt() throws Exception {
		setStock(sanJose, product, 4);
		createUser("recep@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		MockHttpSession receptionist = login("recep@electronica-rojas.test");

		mockMvc.perform(get("/api/v1/branches/{id}/stock", sanJose).session(receptionist))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].quantity").value(4));
		record(receptionist, UUID.randomUUID(), sanJose, "ISSUE", 1, "x").andExpect(status().isForbidden());
		assertThat(stockOf(sanJose, product)).isEqualTo(4);
	}

	@Test
	void retryWithTheSameOperationIdIsAppliedOnce() throws Exception {
		UUID operation = UUID.randomUUID();

		String first = record(manager, operation, sanJose, "RECEIPT", 5, "Compra").andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String replay = record(manager, operation, sanJose, "RECEIPT", 5, "Compra").andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(replay).isEqualTo(first);
		assertThat(stockOf(sanJose, product)).isEqualTo(5);
		assertThat(countRows("stock_movements")).isEqualTo(1);
	}

	@Test
	void reusingAnOperationIdForADifferentRequestIsAConflict() throws Exception {
		UUID operation = UUID.randomUUID();
		record(manager, operation, sanJose, "RECEIPT", 5, "Compra").andExpect(status().isCreated());

		record(manager, operation, sanJose, "RECEIPT", 6, "Compra").andExpect(status().isConflict());
		record(loginAsAdmin(), operation, sanJose, "RECEIPT", 5, "Compra").andExpect(status().isConflict());

		assertThat(stockOf(sanJose, product)).isEqualTo(5);
	}

	@Test
	void stockListIncludesUnstockedProductsFiltersLowStockAndPaginates() throws Exception {
		long second = createProduct("CMP-200");
		createProduct("OLD-1", false);
		setStock(sanJose, product, 3);
		mockMvc.perform(put("/api/v1/branches/{id}/stock/{p}/minimum", sanJose, product).session(manager)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"minimumQuantity\":5}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.minimumQuantity").value(5))
			.andExpect(jsonPath("$.stockStatus").value("LOW"));

		mockMvc.perform(get("/api/v1/branches/{id}/stock", sanJose).session(manager))
			.andExpect(jsonPath("$.content[*].product.sku").value(contains("CMP-100", "CMP-200")))
			.andExpect(jsonPath("$.content[1].quantity").value(0));
		mockMvc.perform(get("/api/v1/branches/{id}/stock", sanJose).param("stockStatus", "LOW").session(manager))
			.andExpect(jsonPath("$.content[*].product.sku").value(contains("CMP-100")));
		mockMvc.perform(get("/api/v1/branches/{id}/stock", sanJose).param("stockStatus", "OUT_OF_STOCK").session(manager))
			.andExpect(jsonPath("$.content[*].product.sku").value(contains("CMP-200")))
			.andExpect(jsonPath("$.content[0].stockStatus").value("OUT_OF_STOCK"));
		mockMvc.perform(get("/api/v1/branches/{id}/stock", sanJose).param("stockStatus", "ATTENTION").session(manager))
			.andExpect(jsonPath("$.totalElements").value(2));
		mockMvc.perform(get("/api/v1/branches/{id}/stock", sanJose).param("size", "1").param("page", "1").session(manager))
			.andExpect(jsonPath("$.content[0].product.id").value(second))
			.andExpect(jsonPath("$.totalPages").value(2));
		mockMvc.perform(get("/api/v1/branches/{id}/stock", sanJose).param("size", "500").session(manager))
			.andExpect(status().isBadRequest());
	}

	@Test
	void historyIsNewestFirstAndFilterable() throws Exception {
		record(manager, UUID.randomUUID(), sanJose, "RECEIPT", 5, "Compra").andExpect(status().isCreated());
		record(manager, UUID.randomUUID(), sanJose, "ISSUE", 2, "Venta").andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/branches/{id}/movements", sanJose).session(manager))
			.andExpect(jsonPath("$.content[*].type").value(contains("ISSUE", "RECEIPT")))
			.andExpect(jsonPath("$.content[0].balanceBefore").value(5))
			.andExpect(jsonPath("$.content[0].balanceAfter").value(3));
		mockMvc.perform(get("/api/v1/branches/{id}/movements", sanJose).param("type", "RECEIPT").session(manager))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void adminStillReadsTheHistoryOfADeactivatedBranch() throws Exception {
		record(manager, UUID.randomUUID(), sanJose, "RECEIPT", 5, "Compra").andExpect(status().isCreated());
		jdbc.update("UPDATE branches SET active = FALSE WHERE id = ?", sanJose);

		mockMvc.perform(get("/api/v1/branches/{id}/movements", sanJose).session(loginAsAdmin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));
		mockMvc.perform(get("/api/v1/branches/{id}/movements", sanJose).session(manager))
			.andExpect(status().isNotFound());
	}

}
