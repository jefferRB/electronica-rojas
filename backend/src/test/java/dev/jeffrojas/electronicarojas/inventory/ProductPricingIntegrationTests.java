package dev.jeffrojas.electronicarojas.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;

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
 * BR-INV-007/008: unit cost and sale price of products (exact decimals, never negative, cost only for
 * roles that may see it) and optional initial stock recorded through the ledger, atomically.
 */
class ProductPricingIntegrationTests extends IntegrationTestSupport {

	@LocalServerPort
	private int port;

	private long sanJose;

	private MockHttpSession admin;

	@BeforeEach
	void setUp() throws Exception {
		sanJose = createBranch("SJ-01");
		admin = loginAsAdmin();
	}

	private ResultActions create(String json) throws Exception {
		return mockMvc.perform(post("/api/v1/products").session(admin).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private static String product(String sku, String extra) {
		return """
				{"sku":"%s","name":"Motor lavadora","category":"Lavadoras","kind":"SPARE_PART"%s}
				""".formatted(sku, extra);
	}

	@Test
	void productWithoutInitialStockHasPricesAndNoStock() throws Exception {
		create(product("MOT-001", ",\"unitCost\":8000,\"salePrice\":12500.5"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.unitCost").value(8000.00))
			.andExpect(jsonPath("$.salePrice").value(12500.50))
			.andExpect(jsonPath("$.chargeableByDefault").value(true));

		assertThat(jdbc.queryForObject("SELECT sale_price FROM products", BigDecimal.class)).isEqualByComparingTo("12500.50");
		assertThat(countRows("branch_stock")).isZero();
		assertThat(countRows("stock_movements")).isZero();
	}

	@Test
	void initialStockIsAReceiptThroughTheLedger() throws Exception {
		String body = create(product("MOT-001",
				",\"salePrice\":12500,\"chargeableByDefault\":false,\"initialStock\":{\"branchId\":%d,\"quantity\":7}"
					.formatted(sanJose)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.chargeableByDefault").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long productId = ((Number) JsonPath.read(body, "$.id")).longValue();

		assertThat(stockOf(sanJose, productId)).isEqualTo(7);
		Map<String, Object> movement = jdbc.queryForMap(
				"SELECT type, quantity_delta, balance_after, reason, branch_id FROM stock_movements WHERE product_id = ?",
				productId);
		assertThat(movement).containsEntry("type", "RECEIPT")
			.containsEntry("quantity_delta", 7)
			.containsEntry("balance_after", 7)
			.containsEntry("reason", ProductCatalogService.INITIAL_STOCK_REASON)
			.containsEntry("branch_id", sanJose);
		// The movement shows up in the branch history like any receipt.
		mockMvc.perform(get("/api/v1/branches/{id}/movements", sanJose).session(admin))
			.andExpect(jsonPath("$.content[0].type").value("RECEIPT"))
			.andExpect(jsonPath("$.content[0].balanceAfter").value(7));
		assertThat(jdbc.queryForList("SELECT action FROM audit_events ORDER BY id", String.class))
			.containsExactly("PRODUCT_CREATED", "STOCK_MOVEMENT_RECORDED");
		assertThat(jdbc.queryForObject("SELECT details->>'initialStock' FROM audit_events WHERE action = 'STOCK_MOVEMENT_RECORDED'",
				String.class)).isEqualTo("true");
	}

	@Test
	void zeroInitialUnitsRecordNothing() throws Exception {
		create(product("MOT-001", ",\"initialStock\":{\"branchId\":%d,\"quantity\":0}".formatted(sanJose)))
			.andExpect(status().isCreated());
		assertThat(countRows("stock_movements")).isZero();
	}

	@Test
	void initialStockAtAnInactiveBranchIsRefusedAndNothingIsCreated() throws Exception {
		long closed = createBranch("OLD-01", false);
		create(product("MOT-001", ",\"initialStock\":{\"branchId\":%d,\"quantity\":3}".formatted(closed)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("BRANCH_INVALID"));
		assertThat(countRows("products")).isZero();
	}

	@Test
	void aFailedInitialStockLeavesNoProduct() throws Exception {
		jdbc.execute("""
				CREATE FUNCTION test_fail_insert() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN RAISE EXCEPTION 'simulated failure'; END; $$
				""");
		jdbc.execute("CREATE TRIGGER test_fail_insert BEFORE INSERT ON stock_movements FOR EACH ROW EXECUTE FUNCTION test_fail_insert()");
		try (BrowserClient browser = BrowserClient.login(port, ADMIN_EMAIL, ADMIN_PASSWORD)) {
			BrowserClient.Response response = browser.postJson("/api/v1/products",
					product("MOT-001", ",\"initialStock\":{\"branchId\":%d,\"quantity\":4}".formatted(sanJose)));
			assertThat(response.status()).isEqualTo(500);
			assertThat(response.body()).doesNotContain("simulated", "SQL", "Exception");
		}
		finally {
			jdbc.execute("DROP TRIGGER test_fail_insert ON stock_movements");
			jdbc.execute("DROP FUNCTION test_fail_insert()");
		}
		assertThat(countRows("products")).isZero();
		assertThat(countRows("branch_stock")).isZero();
		assertThat(countRows("audit_events")).isZero();
	}

	@Test
	void negativeOrOverPreciseAmountsAreRejected() throws Exception {
		create(product("MOT-001", ",\"unitCost\":-1,\"salePrice\":-0.01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("unitCost", "salePrice")));
		create(product("MOT-001", ",\"salePrice\":10.555")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("salePrice"));
		create(product("MOT-001", ",\"initialStock\":{\"branchId\":%d,\"quantity\":-2}".formatted(sanJose)))
			.andExpect(status().isBadRequest());
		assertThat(countRows("products")).isZero();
		// The database refuses a negative amount too, whatever the application does.
		createProduct("DB-1");
		org.assertj.core.api.Assertions
			.assertThatThrownBy(() -> jdbc.update("UPDATE products SET unit_cost = -5 WHERE sku = 'DB-1'"))
			.hasMessageContaining("ck_products_unit_cost");
	}

	@Test
	void theCostReachesOnlyRolesAllowedToSeeIt() throws Exception {
		String body = create(product("MOT-001", ",\"unitCost\":8000,\"salePrice\":12500")).andReturn()
			.getResponse()
			.getContentAsString();
		long productId = ((Number) JsonPath.read(body, "$.id")).longValue();
		createUser("gerente@electronica-rojas.test", Role.BRANCH_MANAGER, sanJose);
		createUser("recepcion@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		MockHttpSession manager = login("gerente@electronica-rojas.test");
		MockHttpSession receptionist = login("recepcion@electronica-rojas.test");

		mockMvc.perform(get("/api/v1/products").session(manager))
			.andExpect(jsonPath("$.content[0].unitCost").value(8000.00));
		mockMvc.perform(get("/api/v1/products").session(receptionist))
			.andExpect(jsonPath("$.content[0].salePrice").value(12500.00))
			.andExpect(jsonPath("$.content[0].unitCost").doesNotExist());
		String detail = mockMvc.perform(get("/api/v1/products/{id}", productId).session(receptionist))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(detail).doesNotContain("8000");
	}

	@Test
	void priceChangesAreAuditedWithBeforeAndAfter() throws Exception {
		String body = create(product("MOT-001", ",\"unitCost\":8000,\"salePrice\":15000")).andReturn()
			.getResponse()
			.getContentAsString();
		long productId = ((Number) JsonPath.read(body, "$.id")).longValue();

		mockMvc.perform(put("/api/v1/products/{id}", productId).session(admin).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name":"Motor lavadora","category":"Lavadoras","kind":"SPARE_PART","unitCost":8000,
					 "salePrice":18000,"chargeableByDefault":true,"active":true,"version":0}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.salePrice").value(18000.00));

		Map<String, Object> event = jdbc.queryForMap("""
				SELECT details->>'salePriceBefore' AS before, details->>'salePriceAfter' AS after
				FROM audit_events WHERE action = 'PRODUCT_PRICING_CHANGED'
				""");
		assertThat(new BigDecimal((String) event.get("before"))).isEqualByComparingTo("15000");
		assertThat(new BigDecimal((String) event.get("after"))).isEqualByComparingTo("18000");

		// Saving the same amounts again (another scale) is not a price change.
		mockMvc.perform(put("/api/v1/products/{id}", productId).session(admin).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name":"Motor de lavadora","category":"Lavadoras","kind":"SPARE_PART","unitCost":8000.00,
					 "salePrice":18000.0,"chargeableByDefault":true,"active":true,"version":1}
					"""))
			.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'PRODUCT_PRICING_CHANGED'",
				Integer.class)).isEqualTo(1);
	}

}
