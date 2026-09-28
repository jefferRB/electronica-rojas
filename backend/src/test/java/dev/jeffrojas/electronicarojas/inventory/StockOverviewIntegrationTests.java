package dev.jeffrojas.electronicarojas.inventory;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;

/**
 * Phase 3 A.3/A.4: consolidated stock with dynamic branch columns, scope applied in SQL, totals
 * only over the columns in scope, stock-status filters and pagination.
 */
class StockOverviewIntegrationTests extends IntegrationTestSupport {

	private long sanJose;

	private long alajuela;

	private long heredia;

	private long compressor;

	private long washer;

	@BeforeEach
	void setUp() {
		sanJose = createBranch("SJ-01");
		alajuela = createBranch("AL-01");
		heredia = createBranch("HE-01");
		createBranch("OLD-01", false);
		compressor = createProduct("CMP-100");
		washer = createProduct("LAV-200");
		setStock(sanJose, compressor, 6);
		setStock(alajuela, compressor, 2);
		setStock(heredia, compressor, 50);
		jdbc.update("UPDATE branch_stock SET minimum_quantity = 5 WHERE branch_id = ? AND product_id = ?", alajuela,
				compressor);
		setStock(sanJose, washer, 3);
	}

	@Test
	void adminSeesOneColumnPerActiveBranchAndTheTotal() throws Exception {
		mockMvc.perform(get("/api/v1/stock/overview").session(loginAsAdmin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.branches[*].code").value(contains("AL-01", "HE-01", "SJ-01")))
			.andExpect(jsonPath("$.rows.content[0].product.sku").value("CMP-100"))
			.andExpect(jsonPath("$.rows.content[0].totalQuantity").value(58))
			.andExpect(jsonPath("$.rows.content[0].cells[*].quantity").value(contains(2, 50, 6)))
			.andExpect(jsonPath("$.rows.content[0].cells[*].stockStatus").value(contains("LOW", "NORMAL", "NORMAL")))
			.andExpect(jsonPath("$.rows.content[1].cells[*].stockStatus")
				.value(contains("OUT_OF_STOCK", "OUT_OF_STOCK", "NORMAL")))
			.andExpect(jsonPath("$.rows.content[1].outOfStockBranches").value(2));
	}

	@Test
	void managerSeesOnlyAssignedBranchesAndTotalsOnlyThose() throws Exception {
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, sanJose, alajuela);

		mockMvc.perform(get("/api/v1/stock/overview").session(login("manager@electronica-rojas.test")))
			.andExpect(jsonPath("$.branches[*].code").value(contains("AL-01", "SJ-01")))
			.andExpect(jsonPath("$.rows.content[0].cells.length()").value(2))
			.andExpect(jsonPath("$.rows.content[0].totalQuantity").value(8));
	}

	@Test
	void stockStatusFiltersLookOnlyAtTheBranchesInScope() throws Exception {
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, heredia);
		var manager = login("manager@electronica-rojas.test");

		mockMvc.perform(get("/api/v1/stock/overview").param("stockStatus", "OUT_OF_STOCK").session(manager))
			.andExpect(jsonPath("$.rows.content[*].product.sku").value(contains("LAV-200")));
		mockMvc.perform(get("/api/v1/stock/overview").param("stockStatus", "LOW").session(manager))
			.andExpect(jsonPath("$.rows.totalElements").value(0));
		mockMvc.perform(get("/api/v1/stock/overview").param("stockStatus", "LOW").session(loginAsAdmin()))
			.andExpect(jsonPath("$.rows.content[*].product.sku").value(contains("CMP-100")));
	}

	@Test
	void searchAndPaginationAreServerSide() throws Exception {
		var admin = loginAsAdmin();

		mockMvc.perform(get("/api/v1/stock/overview").param("size", "1").param("page", "1").session(admin))
			.andExpect(jsonPath("$.rows.content[0].product.sku").value("LAV-200"))
			.andExpect(jsonPath("$.rows.totalPages").value(2));
		mockMvc.perform(get("/api/v1/stock/overview").param("search", "lav").session(admin))
			.andExpect(jsonPath("$.rows.totalElements").value(1));
		mockMvc.perform(get("/api/v1/stock/overview").param("size", "500").session(admin))
			.andExpect(status().isBadRequest());
	}

	@Test
	void techniciansAndUsersWithoutBranchesGetNoStock() throws Exception {
		createUser("tech@electronica-rojas.test", Role.TECHNICIAN, sanJose);
		createUser("recep@electronica-rojas.test", Role.RECEPTIONIST, sanJose);
		jdbc.update("DELETE FROM user_branches WHERE user_id = (SELECT id FROM app_users WHERE email = 'recep@electronica-rojas.test')");

		mockMvc.perform(get("/api/v1/stock/overview").session(login("tech@electronica-rojas.test")))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/stock/overview").session(login("recep@electronica-rojas.test")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.branches.length()").value(0))
			.andExpect(jsonPath("$.rows.totalElements").value(0));
	}

	@Test
	void productDetailUsesTheSameStatuses() throws Exception {
		mockMvc.perform(get("/api/v1/products/{id}", washer).session(loginAsAdmin()))
			// Admin detail lists every branch, inactive OLD-01 included (history stays readable).
			.andExpect(jsonPath("$.stock[*].branch.code").value(contains("AL-01", "HE-01", "OLD-01", "SJ-01")))
			.andExpect(jsonPath("$.stock[*].stockStatus")
				.value(contains("OUT_OF_STOCK", "OUT_OF_STOCK", "OUT_OF_STOCK", "NORMAL")));
	}

}
