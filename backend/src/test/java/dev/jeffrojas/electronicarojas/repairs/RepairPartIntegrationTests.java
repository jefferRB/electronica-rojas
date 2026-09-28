package dev.jeffrojas.electronicarojas.repairs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;

import dev.jeffrojas.electronicarojas.BrowserClient;

/**
 * BR-REP-007 / BR-REP-011..013 against PostgreSQL: consuming spare parts in a repair order through
 * the inventory ledger, rejections that change nothing, idempotent retries, all-or-nothing
 * transactions and corrections that never rewrite history. Concurrency is in
 * RepairPartConcurrencyIntegrationTests.
 */
class RepairPartIntegrationTests extends RepairPartTestSupport {

	@LocalServerPort
	private int port;

	// ---- consumption ----

	@Test
	void consumingOneAndSeveralPartsDiscountsStockThroughMovementsAndAudit() throws Exception {
		long orderId = orderInRepair();
		long belt = createProduct("CORREA-01");
		long pump = createProduct("BOMBA-02");
		setStock(sanJose, belt, 5);
		setStock(sanJose, pump, 3);

		consume(technician, orderId, UUID.randomUUID(), belt, 2).andExpect(status().isCreated())
			.andExpect(jsonPath("$.parts.length()").value(1))
			.andExpect(jsonPath("$.parts[0].product.sku").value("CORREA-01"))
			.andExpect(jsonPath("$.parts[0].quantity").value(2))
			.andExpect(jsonPath("$.parts[0].remainingQuantity").value(2))
			.andExpect(jsonPath("$.parts[0].branch.code").value("SJ-01"))
			.andExpect(jsonPath("$.parts[0].recordedBy.fullName").value("Usuario TECHNICIAN"));
		consume(technician, orderId, UUID.randomUUID(), pump, 1).andExpect(status().isCreated())
			.andExpect(jsonPath("$.parts.length()").value(2))
			.andExpect(jsonPath("$.actions.canConsumeParts").value(true))
			.andExpect(jsonPath("$.actions.canReturnParts").value(true));

		assertThat(stockOf(sanJose, belt)).isEqualTo(3);
		assertThat(stockOf(sanJose, pump)).isEqualTo(2);
		assertThat(jdbc.queryForList("""
				SELECT type || ':' || quantity_delta || ':' || balance_after FROM stock_movements
				WHERE repair_order_id = ? ORDER BY id""", String.class, orderId))
			.containsExactly("OUT_FOR_REPAIR:-2:3", "OUT_FOR_REPAIR:-1:2");
		assertThat(jdbc.queryForList("SELECT action FROM audit_events WHERE action LIKE 'REPAIR_PART%'", String.class))
			.containsExactly("REPAIR_PART_CONSUMED", "REPAIR_PART_CONSUMED");
		assertThat(jdbc.queryForObject(
				"SELECT details->>'orderCode' || ' ' || (details->>'balanceAfter') FROM audit_events WHERE action = 'REPAIR_PART_CONSUMED' ORDER BY id LIMIT 1",
				String.class)).matches("OR-\\d{4}-\\d{6} 3");

		// The inventory history shows the movement with its order, like any other movement.
		mockMvc.perform(get("/api/v1/branches/{id}/movements", sanJose).param("productId", String.valueOf(belt))
			.session(manager))
			.andExpect(jsonPath("$.content[0].type").value("OUT_FOR_REPAIR"))
			.andExpect(jsonPath("$.content[0].repairOrderId").value(orderId))
			.andExpect(jsonPath("$.content[0].repairOrderCode").isString());
	}

	@Test
	void insufficientStockIsRejectedAndChangesNothing() throws Exception {
		long orderId = orderInRepair();
		long part = createProduct("TARJETA-01");
		setStock(sanJose, part, 2);

		consume(technician, orderId, UUID.randomUUID(), part, 3).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
			.andExpect(jsonPath("$.available").value(2))
			.andExpect(jsonPath("$.requested").value(3));
		// A product never stocked at the branch counts as 0 units.
		long never = createProduct("SENSOR-09");
		consume(technician, orderId, UUID.randomUUID(), never, 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.available").value(0));

		assertThat(stockOf(sanJose, part)).isEqualTo(2);
		assertThat(countRows("repair_part_usages") + movementsOfOrder(orderId)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action LIKE 'REPAIR_PART%'",
				Integer.class)).isZero();
	}

	@Test
	void onlyTheWorkshopOfTheOrderMayUseParts() throws Exception {
		long orderId = orderInRepair();
		long part = createProduct("CORREA-01");
		setStock(sanJose, part, 5);

		consume(receptionist, orderId, UUID.randomUUID(), part, 1).andExpect(status().isForbidden());
		// Not the assigned technician, or another branch: the order does not exist for them.
		consume(otherTechnician, orderId, UUID.randomUUID(), part, 1).andExpect(status().isNotFound());
		consume(alajuelaManager, orderId, UUID.randomUUID(), part, 1).andExpect(status().isNotFound());
		// No CSRF token: refused before reaching the use case.
		mockMvc.perform(post("/api/v1/repair-orders/{id}/parts", orderId).session(technician)
			.contentType(MediaType.APPLICATION_JSON)
			.content(consumeJson(UUID.randomUUID(), part, 1))).andExpect(status().isForbidden());
		// Anonymous.
		mockMvc.perform(post("/api/v1/repair-orders/{id}/parts", orderId).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(consumeJson(UUID.randomUUID(), part, 1))).andExpect(status().isUnauthorized());

		assertThat(stockOf(sanJose, part)).isEqualTo(5);
		assertThat(countRows("repair_part_usages")).isZero();

		consume(manager, orderId, UUID.randomUUID(), part, 1).andExpect(status().isCreated());
		assertThat(stockOf(sanJose, part)).isEqualTo(4);
	}

	@Test
	void theTechnicianSeesSparePartsOfTheOrderButStillHasNoInventoryModule() throws Exception {
		long orderId = orderInRepair();
		long part = createProduct("CORREA-01");
		setStock(sanJose, part, 4);
		jdbc.update("""
				INSERT INTO products (sku, name, category, kind, active, created_at, updated_at)
				VALUES ('TV-55', 'Televisor 55', 'Televisores', 'MERCHANDISE', TRUE, now(), now())""");

		mockMvc.perform(get("/api/v1/repair-orders/{id}/parts/options", orderId).param("search", "")
			.session(technician))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].product.sku").value("CORREA-01"))
			.andExpect(jsonPath("$.content[0].quantity").value(4));
		mockMvc.perform(get("/api/v1/repair-orders/{id}/parts/options", orderId).session(receptionist))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/repair-orders/{id}/parts/options", orderId).session(otherTechnician))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/branches/{id}/stock", sanJose).session(technician))
			.andExpect(status().isForbidden());
	}

	@Test
	void theOrderMustBeInRepairAndThePartAnActiveSparePart() throws Exception {
		long orderId = orderInRepair();
		long inactive = createProduct("VIEJO-01", false);
		setStock(sanJose, inactive, 5);
		long merchandise = jdbc.queryForObject("""
				INSERT INTO products (sku, name, category, kind, active, created_at, updated_at)
				VALUES ('TV-55', 'Televisor 55', 'Televisores', 'MERCHANDISE', TRUE, now(), now()) RETURNING id""",
				Long.class);
		setStock(sanJose, merchandise, 5);

		consume(technician, orderId, UUID.randomUUID(), inactive, 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRODUCT_INACTIVE"));
		consume(technician, orderId, UUID.randomUUID(), merchandise, 1).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("NOT_A_SPARE_PART"));
		consume(technician, orderId, UUID.randomUUID(), 999_999, 1).andExpect(status().isNotFound());
		consume(technician, orderId, UUID.randomUUID(), inactive, 0).andExpect(status().isBadRequest());

		long part = createProduct("CORREA-01");
		setStock(sanJose, part, 5);
		transition(technician, orderId, "READY_FOR_PICKUP").andExpect(status().isOk());
		consume(technician, orderId, UUID.randomUUID(), part, 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PARTS_NOT_ALLOWED"))
			.andExpect(jsonPath("$.status").value("READY_FOR_PICKUP"));

		assertThat(stockOf(sanJose, part) + stockOf(sanJose, merchandise) + stockOf(sanJose, inactive)).isEqualTo(15);
		assertThat(countRows("repair_part_usages")).isZero();
	}

	// ---- idempotency ----

	@Test
	void aRetryOfTheSameConsumptionIsAppliedOnce() throws Exception {
		long orderId = orderInRepair();
		long part = createProduct("CORREA-01");
		setStock(sanJose, part, 5);
		UUID operationId = UUID.randomUUID();

		consume(technician, orderId, operationId, part, 2).andExpect(status().isCreated());
		consume(technician, orderId, operationId, part, 2).andExpect(status().isOk())
			.andExpect(jsonPath("$.parts.length()").value(1));

		assertThat(stockOf(sanJose, part)).isEqualTo(3);
		assertThat(countRows("repair_part_usages")).isEqualTo(1);
		assertThat(movementsOfOrder(orderId)).isEqualTo(1);

		// Same id with other data, by someone else, or already spent on a stock movement: 409.
		consume(technician, orderId, operationId, part, 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OPERATION_ID_REUSED"));
		consume(manager, orderId, operationId, part, 2).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OPERATION_ID_REUSED"));
		UUID stockOperation = UUID.randomUUID();
		mockMvc.perform(post("/api/v1/stock-movements").session(manager)
			.with(xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"operationId":"%s","branchId":%d,"productId":%d,"type":"RECEIPT","quantity":1,"reason":"Compra"}
					""".formatted(stockOperation, sanJose, part))).andExpect(status().isCreated());
		consume(manager, orderId, stockOperation, part, 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OPERATION_ID_REUSED"));

		assertThat(stockOf(sanJose, part)).isEqualTo(4);
		assertThat(countRows("repair_part_usages")).isEqualTo(1);
	}

	// ---- all or nothing ----

	@Test
	void aFailureWritingTheAuditRollsBackStockAndThePartLine() throws Exception {
		long orderId = orderInRepair();
		long part = createProduct("CORREA-01");
		setStock(sanJose, part, 5);
		assertFailureLeavesNothing("audit_events", orderId, part);
	}

	@Test
	void aFailureWritingThePartLineRollsBackTheStockMovement() throws Exception {
		long orderId = orderInRepair();
		long part = createProduct("CORREA-01");
		setStock(sanJose, part, 5);
		assertFailureLeavesNothing("repair_part_usages", orderId, part);
	}

	/** A trigger makes the insert into {@code table} fail after the stock was already changed. */
	private void assertFailureLeavesNothing(String table, long orderId, long part) throws Exception {
		int auditBefore = countRows("audit_events");
		jdbc.execute("""
				CREATE FUNCTION test_fail_insert() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN RAISE EXCEPTION 'simulated failure'; END; $$
				""");
		jdbc.execute("CREATE TRIGGER test_fail_insert BEFORE INSERT ON " + table
				+ " FOR EACH ROW EXECUTE FUNCTION test_fail_insert()");
		try (BrowserClient browser = BrowserClient.login(port, "tecnico@electronica-rojas.test", USER_PASSWORD)) {
			BrowserClient.Response response = browser.postJson("/api/v1/repair-orders/" + orderId + "/parts",
					consumeJson(UUID.randomUUID(), part, 2));
			assertThat(response.status()).isEqualTo(500);
			assertThat(response.body()).doesNotContain("simulated", "SQL", "Exception");
		}
		finally {
			jdbc.execute("DROP TRIGGER test_fail_insert ON " + table);
			jdbc.execute("DROP FUNCTION test_fail_insert()");
		}
		assertThat(stockOf(sanJose, part)).isEqualTo(5);
		assertThat(countRows("repair_part_usages")).isZero();
		assertThat(movementsOfOrder(orderId)).isZero();
		assertThat(countRows("audit_events")).isEqualTo(auditBefore);
	}

	// ---- corrections ----

	@Test
	void partialAndCompleteReturnsKeepTheOriginalConsumption() throws Exception {
		long orderId = orderInRepair();
		long part = createProduct("CORREA-01");
		setStock(sanJose, part, 5);
		consume(technician, orderId, UUID.randomUUID(), part, 3).andExpect(status().isCreated());
		long usageId = usageIdOf(orderId, part);

		returnPart(technician, orderId, usageId, UUID.randomUUID(), 1, "Sobró una unidad")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.parts[0].quantity").value(3))
			.andExpect(jsonPath("$.parts[0].returnedQuantity").value(1))
			.andExpect(jsonPath("$.parts[0].remainingQuantity").value(2))
			.andExpect(jsonPath("$.parts[0].returns[0].reason").value("Sobró una unidad"))
			.andExpect(jsonPath("$.parts[0].returns[0].orderStatus").value("IN_REPAIR"));
		assertThat(stockOf(sanJose, part)).isEqualTo(3);

		// More than what is still counted as used: refused, nothing changes.
		returnPart(technician, orderId, usageId, UUID.randomUUID(), 3, "Error").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RETURN_EXCEEDS_CONSUMED"))
			.andExpect(jsonPath("$.remaining").value(2));

		returnPart(technician, orderId, usageId, UUID.randomUUID(), 2, "Repuesto equivocado")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.parts[0].remainingQuantity").value(0))
			.andExpect(jsonPath("$.actions.canReturnParts").value(false));
		// The same quantity can never be returned twice.
		returnPart(technician, orderId, usageId, UUID.randomUUID(), 1, "Otra vez").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RETURN_EXCEEDS_CONSUMED"));

		assertThat(stockOf(sanJose, part)).isEqualTo(5);
		assertThat(jdbc.queryForList("SELECT type || ':' || quantity_delta FROM stock_movements WHERE repair_order_id = ? ORDER BY id",
				String.class, orderId))
			.containsExactly("OUT_FOR_REPAIR:-3", "RETURN_FROM_REPAIR:1", "RETURN_FROM_REPAIR:2");
		assertThat(jdbc.queryForObject("SELECT quantity FROM repair_part_usages WHERE id = ?", Integer.class, usageId))
			.isEqualTo(3);
		assertThat(jdbc.queryForList("SELECT action FROM audit_events WHERE action LIKE 'REPAIR_PART%' ORDER BY id",
				String.class))
			.containsExactly("REPAIR_PART_CONSUMED", "REPAIR_PART_RETURNED", "REPAIR_PART_RETURNED");
		// The free-text reason stays on the order, not in the audit trail.
		assertThat(jdbc.queryForList("SELECT summary || coalesce(details::text, '') FROM audit_events", String.class))
			.allSatisfy(text -> assertThat(text).doesNotContain("equivocado"));
	}

	@Test
	void aRetriedReturnIsAppliedOnce() throws Exception {
		long orderId = orderInRepair();
		long part = createProduct("CORREA-01");
		setStock(sanJose, part, 5);
		consume(technician, orderId, UUID.randomUUID(), part, 2).andExpect(status().isCreated());
		long usageId = usageIdOf(orderId, part);
		UUID operationId = UUID.randomUUID();

		returnPart(technician, orderId, usageId, operationId, 1, "Sobró").andExpect(status().isCreated());
		returnPart(technician, orderId, usageId, operationId, 1, "Sobró").andExpect(status().isOk());
		returnPart(technician, orderId, usageId, operationId, 2, "Sobró").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("OPERATION_ID_REUSED"));

		assertThat(stockOf(sanJose, part)).isEqualTo(4);
		assertThat(countRows("repair_part_returns")).isEqualTo(1);
	}

	@Test
	void closedOrdersAreCorrectedOnlyByManagementWithoutTouchingTheirStatusHistory() throws Exception {
		long orderId = orderInRepair();
		long part = createProduct("CORREA-01");
		setStock(sanJose, part, 5);
		consume(technician, orderId, UUID.randomUUID(), part, 2).andExpect(status().isCreated());
		long usageId = usageIdOf(orderId, part);
		transition(technician, orderId, "READY_FOR_PICKUP").andExpect(status().isOk());
		transition(receptionist, orderId, "DELIVERED").andExpect(status().isOk());
		List<String> historyBefore = jdbc.queryForList(
				"SELECT to_status || changed_at FROM repair_status_history WHERE order_id = ? ORDER BY id", String.class,
				orderId);

		returnPart(technician, orderId, usageId, UUID.randomUUID(), 1, "Error de registro")
			.andExpect(status().isForbidden());
		returnPart(receptionist, orderId, usageId, UUID.randomUUID(), 1, "Error de registro")
			.andExpect(status().isForbidden());
		returnPart(alajuelaManager, orderId, usageId, UUID.randomUUID(), 1, "Error de registro")
			.andExpect(status().isNotFound());
		// A line of another order is not reachable through this one.
		long otherOrder = orderInRepair();
		returnPart(manager, otherOrder, usageId, UUID.randomUUID(), 1, "Error").andExpect(status().isNotFound());

		returnPart(manager, orderId, usageId, UUID.randomUUID(), 1, "Error de registro").andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DELIVERED"))
			.andExpect(jsonPath("$.parts[0].returns[0].orderStatus").value("DELIVERED"));

		assertThat(stockOf(sanJose, part)).isEqualTo(4);
		assertThat(jdbc.queryForObject(
				"SELECT details->>'orderClosed' FROM audit_events WHERE action = 'REPAIR_PART_RETURNED'", String.class))
			.isEqualTo("true");
		assertThat(jdbc.queryForList("SELECT to_status || changed_at FROM repair_status_history WHERE order_id = ? ORDER BY id",
				String.class, orderId)).isEqualTo(historyBefore);
	}

	// ---- database guards ----

	@Test
	void partHistoryCannotBeRewrittenOrDeleted() throws Exception {
		long orderId = orderInRepair();
		long part = createProduct("CORREA-01");
		setStock(sanJose, part, 5);
		consume(technician, orderId, UUID.randomUUID(), part, 2).andExpect(status().isCreated());
		long usageId = usageIdOf(orderId, part);
		returnPart(technician, orderId, usageId, UUID.randomUUID(), 1, "Sobró").andExpect(status().isCreated());

		assertThatThrownBy(() -> jdbc.update("UPDATE repair_part_usages SET quantity = 1 WHERE id = ?", usageId))
			.hasMessageContaining("Only the returned quantity");
		assertThatThrownBy(() -> jdbc.update("UPDATE repair_part_usages SET returned_quantity = 0 WHERE id = ?", usageId))
			.hasMessageContaining("Only the returned quantity");
		assertThatThrownBy(() -> jdbc.update("UPDATE repair_part_usages SET returned_quantity = 3 WHERE id = ?", usageId))
			.hasMessageContaining("ck_repair_part_usages_returned");
		assertThatThrownBy(() -> jdbc.update("DELETE FROM repair_part_usages WHERE id = ?", usageId))
			.hasMessageContaining("cannot be deleted");
		assertThatThrownBy(() -> jdbc.update("DELETE FROM repair_part_returns")).hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.update("UPDATE stock_movements SET quantity_delta = -1 WHERE repair_order_id = ?",
				orderId)).hasMessageContaining("append-only");
		// A repair movement must point to its order, and only repair movements may.
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO stock_movements (operation_id, type, branch_id, product_id, quantity_delta, balance_after,
				    request_fingerprint, actor_id, created_at)
				VALUES (gen_random_uuid(), 'OUT_FOR_REPAIR', ?, ?, -1, 0, 'x', ?, now())""", sanJose, part, technicianId))
			.hasMessageContaining("ck_stock_movements_repair_link");
	}

}
