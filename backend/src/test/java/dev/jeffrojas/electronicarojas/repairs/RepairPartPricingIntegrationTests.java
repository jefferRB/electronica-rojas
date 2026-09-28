package dev.jeffrojas.electronicarojas.repairs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

/**
 * BR-REP-014: each part line freezes its price, cost and charging decision when it is recorded;
 * "not charged" still uses stock; returns lower the chargeable subtotal; overrides are management
 * decisions that never touch the catalog; costs reach only the roles allowed to see them.
 */
class RepairPartPricingIntegrationTests extends RepairPartTestSupport {

	private long motor;

	private long orderId;

	@BeforeEach
	void setUpPart() throws Exception {
		motor = createProduct("MOT-001");
		jdbc.update("UPDATE products SET sale_price = 12500, unit_cost = 8000 WHERE id = ?", motor);
		setStock(sanJose, motor, 10);
		orderId = orderInRepair();
	}

	private ResultActions consumePriced(MockHttpSession session, UUID operationId, long productId, int quantity,
			String pricing) throws Exception {
		return mockMvc.perform(post("/api/v1/repair-orders/{id}/parts", orderId).session(session)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"operationId\":\"%s\",\"productId\":%d,\"quantity\":%d%s}".formatted(operationId, productId,
					quantity, pricing)));
	}

	private ResultActions detail(MockHttpSession session) throws Exception {
		return mockMvc.perform(get("/api/v1/repair-orders/{id}", orderId).session(session));
	}

	@Test
	void aLineFreezesTheCatalogPriceAndTheCostIsOnlyForManagement() throws Exception {
		consume(technician, orderId, UUID.randomUUID(), motor, 2).andExpect(status().isCreated())
			.andExpect(jsonPath("$.parts[0].unitPrice").value(12500.00))
			.andExpect(jsonPath("$.parts[0].chargeable").value(true))
			.andExpect(jsonPath("$.parts[0].priceOverridden").value(false))
			.andExpect(jsonPath("$.parts[0].chargedAmount").value(25000.00))
			.andExpect(jsonPath("$.parts[0].unitCost").doesNotExist())
			.andExpect(jsonPath("$.partsSummary.unitsInUse").value(2))
			.andExpect(jsonPath("$.partsSummary.chargeableSubtotal").value(25000.00))
			.andExpect(jsonPath("$.partsSummary.totalCost").doesNotExist())
			.andExpect(jsonPath("$.partsSummary.currency").value("CRC"))
			.andExpect(jsonPath("$.actions.canOverridePartPricing").value(false));

		detail(manager).andExpect(jsonPath("$.parts[0].unitCost").value(8000.00))
			.andExpect(jsonPath("$.partsSummary.totalCost").value(16000.00))
			.andExpect(jsonPath("$.actions.canOverridePartPricing").value(true));
		detail(receptionist).andExpect(jsonPath("$.parts[0].unitCost").doesNotExist())
			.andExpect(jsonPath("$.partsSummary.chargeableSubtotal").value(25000.00));
	}

	@Test
	void aCatalogPriceChangeNeverRewritesARecordedLine() throws Exception {
		consume(technician, orderId, UUID.randomUUID(), motor, 1).andExpect(status().isCreated());
		mockMvc.perform(put("/api/v1/products/{id}", motor).session(loginAsAdmin()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name":"Producto MOT-001","category":"Repuestos","kind":"SPARE_PART","unitCost":9000,
					 "salePrice":18000,"chargeableByDefault":true,"active":true,"version":0}
					"""))
			.andExpect(status().isOk());

		consume(technician, orderId, UUID.randomUUID(), motor, 1).andExpect(status().isCreated());
		detail(manager).andExpect(jsonPath("$.parts[0].unitPrice").value(12500.00))
			.andExpect(jsonPath("$.parts[0].unitCost").value(8000.00))
			.andExpect(jsonPath("$.parts[1].unitPrice").value(18000.00))
			.andExpect(jsonPath("$.parts[1].unitCost").value(9000.00))
			.andExpect(jsonPath("$.partsSummary.chargeableSubtotal").value(30500.00));
	}

	@Test
	void aLineWithoutChargeStillUsesStockAndStaysInTheHistory() throws Exception {
		consumePriced(manager, UUID.randomUUID(), motor, 3, ",\"chargeable\":false").andExpect(status().isCreated())
			.andExpect(jsonPath("$.parts[0].chargeable").value(false))
			// Not charged is not "price 0": the line keeps its price.
			.andExpect(jsonPath("$.parts[0].unitPrice").value(12500.00))
			.andExpect(jsonPath("$.parts[0].chargedAmount").value(0))
			.andExpect(jsonPath("$.partsSummary.unitsInUse").value(3))
			.andExpect(jsonPath("$.partsSummary.unitsWithoutCharge").value(3))
			.andExpect(jsonPath("$.partsSummary.chargeableSubtotal").value(0));

		assertThat(stockOf(sanJose, motor)).isEqualTo(7);
		assertThat(movementsOfOrder(orderId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT details->>'chargeOverridden' FROM audit_events WHERE action = 'REPAIR_PART_CONSUMED'",
				String.class)).isEqualTo("true");
	}

	@Test
	void theProductDefaultDecidesWhenTheLineSaysNothing() throws Exception {
		jdbc.update("UPDATE products SET chargeable_by_default = FALSE WHERE id = ?", motor);
		consume(technician, orderId, UUID.randomUUID(), motor, 1).andExpect(status().isCreated())
			.andExpect(jsonPath("$.parts[0].chargeable").value(false));
	}

	@Test
	void aPriceSetForOneLineStaysOnThatLine() throws Exception {
		consumePriced(manager, UUID.randomUUID(), motor, 2, ",\"unitPrice\":10000").andExpect(status().isCreated())
			.andExpect(jsonPath("$.parts[0].unitPrice").value(10000.00))
			.andExpect(jsonPath("$.parts[0].priceOverridden").value(true))
			.andExpect(jsonPath("$.partsSummary.chargeableSubtotal").value(20000.00));

		assertThat(jdbc.queryForObject("SELECT sale_price FROM products WHERE id = ?", BigDecimal.class, motor))
			.isEqualByComparingTo("12500");
		assertThat(jdbc.queryForObject("SELECT details->>'priceOverridden' FROM audit_events WHERE action = 'REPAIR_PART_CONSUMED'",
				String.class)).isEqualTo("true");
	}

	@Test
	void techniciansRecordPartsOnlyWithTheCatalogDefaults() throws Exception {
		consumePriced(technician, UUID.randomUUID(), motor, 1, ",\"unitPrice\":1").andExpect(status().isForbidden());
		consumePriced(technician, UUID.randomUUID(), motor, 1, ",\"chargeable\":false").andExpect(status().isForbidden());
		// The same values as the catalog are not a change.
		consumePriced(technician, UUID.randomUUID(), motor, 1, ",\"unitPrice\":12500,\"chargeable\":true")
			.andExpect(status().isCreated());

		assertThat(stockOf(sanJose, motor)).isEqualTo(9);
		assertThat(countRows("repair_part_usages")).isEqualTo(1);
	}

	@Test
	void negativeLinePricesAreRejected() throws Exception {
		consumePriced(manager, UUID.randomUUID(), motor, 1, ",\"unitPrice\":-5").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("unitPrice"));
		assertThat(stockOf(sanJose, motor)).isEqualTo(10);
	}

	@Test
	void returnsLowerTheChargeableSubtotalWithoutRewritingTheLine() throws Exception {
		consume(technician, orderId, UUID.randomUUID(), motor, 3).andExpect(status().isCreated())
			.andExpect(jsonPath("$.partsSummary.chargeableSubtotal").value(37500.00));
		long usageId = usageIdOf(orderId, motor);

		returnPart(technician, orderId, usageId, UUID.randomUUID(), 1, "Sobró una unidad").andExpect(status().isCreated())
			.andExpect(jsonPath("$.parts[0].quantity").value(3))
			.andExpect(jsonPath("$.parts[0].remainingQuantity").value(2))
			.andExpect(jsonPath("$.parts[0].unitPrice").value(12500.00))
			.andExpect(jsonPath("$.parts[0].chargedAmount").value(25000.00))
			.andExpect(jsonPath("$.partsSummary.chargeableSubtotal").value(25000.00))
			.andExpect(jsonPath("$.partsSummary.unitsInUse").value(2));
		assertThat(stockOf(sanJose, motor)).isEqualTo(8);
	}

	@Test
	void theSameOperationWithTheSamePricingIsAppliedOnce() throws Exception {
		UUID operation = UUID.randomUUID();
		consumePriced(manager, operation, motor, 2, ",\"unitPrice\":11000").andExpect(status().isCreated());
		consumePriced(manager, operation, motor, 2, ",\"unitPrice\":11000.00").andExpect(status().isOk());
		consumePriced(manager, operation, motor, 2, ",\"unitPrice\":9000").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OPERATION_ID_REUSED"));

		assertThat(countRows("repair_part_usages")).isEqualTo(1);
		assertThat(stockOf(sanJose, motor)).isEqualTo(8);
	}

	@Test
	void aChargeablePartWithoutPriceIsRecordedAndFlagged() throws Exception {
		long noPrice = createProduct("SIN-PRECIO");
		setStock(sanJose, noPrice, 2);

		consume(technician, orderId, UUID.randomUUID(), noPrice, 1).andExpect(status().isCreated())
			.andExpect(jsonPath("$.parts[0].unitPrice").doesNotExist())
			.andExpect(jsonPath("$.parts[0].chargedAmount").doesNotExist())
			.andExpect(jsonPath("$.partsSummary.unpricedLines").value(1))
			.andExpect(jsonPath("$.partsSummary.chargeableSubtotal").value(0));
		assertThat(stockOf(sanJose, noPrice)).isEqualTo(1);
	}

	@Test
	void partOptionsCarryTheDefaultsButNeverTheCost() throws Exception {
		mockMvc.perform(get("/api/v1/repair-orders/{id}/parts/options", orderId).session(technician))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].product.sku").value("MOT-001"))
			.andExpect(jsonPath("$.content[0].quantity").value(10))
			.andExpect(jsonPath("$.content[0].salePrice").value(12500.00))
			.andExpect(jsonPath("$.content[0].chargeableByDefault").value(true))
			.andExpect(jsonPath("$.content[0].unitCost").doesNotExist());
	}

	@Test
	void theDatabaseKeepsThePricingSnapshotImmutable() throws Exception {
		consume(technician, orderId, UUID.randomUUID(), motor, 1).andExpect(status().isCreated());
		long usageId = usageIdOf(orderId, motor);

		assertThatThrownBy(() -> jdbc.update("UPDATE repair_part_usages SET unit_price = 1 WHERE id = ?", usageId))
			.hasMessageContaining("Only the returned quantity");
		assertThatThrownBy(() -> jdbc.update("UPDATE repair_part_usages SET chargeable = FALSE WHERE id = ?", usageId))
			.hasMessageContaining("Only the returned quantity");
		assertThatThrownBy(() -> jdbc.update("UPDATE repair_part_usages SET unit_cost = NULL WHERE id = ?", usageId))
			.hasMessageContaining("Only the returned quantity");
	}

}
