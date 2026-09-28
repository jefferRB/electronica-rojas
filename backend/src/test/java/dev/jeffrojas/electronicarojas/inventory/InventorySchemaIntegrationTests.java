package dev.jeffrojas.electronicarojas.inventory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.security.Role;

/**
 * DATA-001 / DATA-005: PostgreSQL enforces the inventory invariants even for writes that bypass
 * the services, and the history tables cannot be rewritten.
 */
class InventorySchemaIntegrationTests extends IntegrationTestSupport {

	private long branch;

	private long otherBranch;

	private long product;

	private long actor;

	@BeforeEach
	void setUp() {
		branch = createBranch("SJ-01");
		otherBranch = createBranch("AL-01");
		product = createProduct("CMP-100");
		actor = createUser("manager@electronica-rojas.test", Role.BRANCH_MANAGER, branch);
	}

	private void insertMovement(String type, int delta, int balanceAfter, String reason) {
		jdbc.update("""
				INSERT INTO stock_movements (operation_id, type, branch_id, product_id, quantity_delta, balance_after,
				                             reason, request_fingerprint, actor_id, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, 'fp', ?, now())
				""", UUID.randomUUID(), type, branch, product, delta, balanceAfter, reason, actor);
	}

	@Test
	void stockCannotBeNegativeNorDuplicatedPerBranchAndProduct() {
		setStock(branch, product, 1);

		assertThatThrownBy(() -> jdbc.update("UPDATE branch_stock SET quantity = -1"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO branch_stock (branch_id, product_id, quantity, updated_at) VALUES (?, ?, 5, now())
				""", branch, product)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void skuMustBeNormalizedAndKindKnown() {
		assertThatThrownBy(() -> createProduct("lower-1")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO products (sku, name, category, kind, created_at, updated_at)
				VALUES ('X-1', 'x', 'y', 'SERVICE', now(), now())
				""")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void movementsNeedANonZeroDeltaWithTheSignOfTheirType() {
		assertThatThrownBy(() -> insertMovement("RECEIPT", 0, 0, "x")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertMovement("RECEIPT", -1, 0, "x")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertMovement("ISSUE", 1, 1, "x")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertMovement("ISSUE", -1, -1, "x")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void standaloneMovementsNeedAReasonAndTransferLegsATransfer() {
		assertThatThrownBy(() -> insertMovement("RECEIPT", 1, 1, null)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertMovement("TRANSFER_IN", 1, 1, null))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void transfersNeedDistinctBranchesAndAUniqueOperationId() {
		UUID operation = UUID.randomUUID();
		String insert = """
				INSERT INTO stock_transfers (operation_id, source_branch_id, destination_branch_id, product_id, quantity,
				                             request_fingerprint, actor_id, created_at)
				VALUES (?, ?, ?, ?, 1, 'fp', ?, now())
				""";

		assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), branch, branch, product, actor))
			.isInstanceOf(DataIntegrityViolationException.class);
		jdbc.update(insert, operation, branch, otherBranch, product, actor);
		assertThatThrownBy(() -> jdbc.update(insert, operation, branch, otherBranch, product, actor))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void historyTablesAreAppendOnly() {
		insertMovement("RECEIPT", 5, 5, "Compra");
		jdbc.update("""
				INSERT INTO audit_events (occurred_at, action, entity_type, entity_id, summary)
				VALUES (now(), 'PRODUCT_CREATED', 'PRODUCT', '1', 'x')
				""");

		assertThatThrownBy(() -> jdbc.update("UPDATE stock_movements SET quantity_delta = 50"))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbc.update("DELETE FROM stock_movements")).isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbc.update("UPDATE audit_events SET summary = 'rewritten'"))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_events")).isInstanceOf(DataAccessException.class);
	}

	@Test
	void productsWithHistoryCannotBeDeleted() {
		insertMovement("RECEIPT", 5, 5, "Compra");

		assertThatThrownBy(() -> jdbc.update("DELETE FROM products WHERE id = ?", product))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
