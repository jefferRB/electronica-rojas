package dev.jeffrojas.electronicarojas.inventory;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;

/** BR-INV-001/004, FR-INV-002/003: global catalog, roles and filters. */
class ProductCatalogIntegrationTests extends IntegrationTestSupport {

	private static final String NEW_PRODUCT = """
			{"sku":" cmp-100 ","name":"Compresor 1/4 HP","category":"Refrigeración","description":"Repuesto","kind":"SPARE_PART"}
			""";

	@Test
	void adminCreatesProductWithNormalizedSku() throws Exception {
		mockMvc.perform(post("/api/v1/products").session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(NEW_PRODUCT))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.sku").value("CMP-100"))
			.andExpect(jsonPath("$.unit").value("UNIT"))
			.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void skuIsUniqueIgnoringCase() throws Exception {
		createProduct("CMP-100");

		mockMvc.perform(post("/api/v1/products").session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(NEW_PRODUCT)).andExpect(status().isConflict());
	}

	@Test
	void invalidProductsGetFieldErrors() throws Exception {
		mockMvc.perform(post("/api/v1/products").session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"sku\":\"a b\",\"name\":\"\",\"category\":\"x\",\"kind\":\"SPARE_PART\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("sku", "name")));
	}

	@Test
	void onlyAdminsMaintainTheCatalog() throws Exception {
		long branch = createBranch("SJ-01");
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, branch);

		mockMvc.perform(post("/api/v1/products").session(login("manager@electronica-rojas.test")).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(NEW_PRODUCT)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/products").with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void techniciansHaveNoDirectInventoryAccess() throws Exception {
		long branch = createBranch("SJ-01");
		createUser("tech@electronica-rojas.test", Role.TECHNICIAN, branch);
		MockHttpSession tech = login("tech@electronica-rojas.test");

		mockMvc.perform(get("/api/v1/products").session(tech)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/branches/{id}/stock", branch).session(tech)).andExpect(status().isForbidden());
	}

	@Test
	void receptionistsReadTheCatalog() throws Exception {
		createProduct("CMP-100");
		createUser("recep@electronica-rojas.test", Role.RECEPTIONIST, createBranch("SJ-01"));

		mockMvc.perform(get("/api/v1/products").session(login("recep@electronica-rojas.test")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].sku").value("CMP-100"));
	}

	@Test
	void searchFiltersAndPaginatesByStableSkuOrder() throws Exception {
		createProduct("MOT-2");
		createProduct("MOT-1");
		createProduct("OLD-1", false);
		jdbc.update("UPDATE products SET category = 'Motores', kind = 'MERCHANDISE' WHERE sku LIKE 'MOT-%'");
		MockHttpSession admin = loginAsAdmin();

		mockMvc.perform(get("/api/v1/products").param("search", "mot").param("size", "1").session(admin))
			.andExpect(jsonPath("$.content[*].sku").value(contains("MOT-1")))
			.andExpect(jsonPath("$.totalElements").value(2));
		mockMvc.perform(get("/api/v1/products").param("category", "Motores").param("kind", "MERCHANDISE").session(admin))
			.andExpect(jsonPath("$.content[*].sku").value(contains("MOT-1", "MOT-2")));
		mockMvc.perform(get("/api/v1/products").param("status", "INACTIVE").session(admin))
			.andExpect(jsonPath("$.content[*].sku").value(contains("OLD-1")));
		mockMvc.perform(get("/api/v1/products").param("status", "ALL").session(admin))
			.andExpect(jsonPath("$.totalElements").value(3));
		mockMvc.perform(get("/api/v1/products/categories").session(admin))
			.andExpect(jsonPath("$").value(contains("Motores", "Repuestos")));
	}

	@Test
	void likeWildcardsInSearchAreLiteral() throws Exception {
		createProduct("AB-1");
		createProduct("A_1");

		mockMvc.perform(get("/api/v1/products").param("search", "_").session(loginAsAdmin()))
			.andExpect(jsonPath("$.content[*].sku").value(contains("A_1")));
	}

	@Test
	void adminDeactivatesWithOptimisticVersion() throws Exception {
		long product = createProduct("CMP-100");
		MockHttpSession admin = loginAsAdmin();
		String update = """
				{"name":"Compresor","category":"Refrigeración","kind":"SPARE_PART","active":false,"version":%d}
				""";

		mockMvc.perform(put("/api/v1/products/{id}", product).session(admin).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(0)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false))
			.andExpect(jsonPath("$.version").value(1));
		mockMvc.perform(put("/api/v1/products/{id}", product).session(admin).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(update.formatted(0))).andExpect(status().isConflict());
	}

	@Test
	void productDetailShowsStockOnlyForBranchesInScope() throws Exception {
		long own = createBranch("SJ-01");
		long foreign = createBranch("AL-01");
		long product = createProduct("CMP-100");
		setStock(own, product, 7);
		setStock(foreign, product, 50);
		createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, own);

		mockMvc.perform(get("/api/v1/products/{id}", product).session(login("manager@electronica-rojas.test")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stock.length()").value(1))
			.andExpect(jsonPath("$.stock[0].branch.code").value("SJ-01"))
			.andExpect(jsonPath("$.stock[0].quantity").value(7));
		mockMvc.perform(get("/api/v1/products/{id}", product).session(loginAsAdmin()))
			.andExpect(jsonPath("$.stock[*].quantity").value(contains(50, 7)));
		mockMvc.perform(get("/api/v1/products/{id}", product + 999).session(loginAsAdmin()))
			.andExpect(status().isNotFound());
	}

}
