package dev.jeffrojas.electronicarojas.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;

import dev.jeffrojas.electronicarojas.IntegrationTestSupport;
import dev.jeffrojas.electronicarojas.dashboard.DashboardDtos.DashboardResponse;
import dev.jeffrojas.electronicarojas.dashboard.DashboardDtos.Indicator;
import dev.jeffrojas.electronicarojas.dashboard.DashboardService;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.security.Role;

/**
 * The DEV-ONLY demo loader against real PostgreSQL: it reuses the existing branches and people, goes
 * through the real use cases, is idempotent, and leaves every invariant intact (stock ledger,
 * transitions, agenda, transfers, parts, audit, outbox). At context start the database has no
 * branches, so the startup run skips; each test prepares the data and calls the loader.
 */
@TestPropertySource(properties = "app.demo.seed=true")
class DemoDataSeederIntegrationTests extends IntegrationTestSupport {

	private static final List<String> TABLES = List.of("branches", "app_users", "products", "branch_stock",
			"stock_movements", "stock_transfers", "customers", "customer_consents", "repair_orders",
			"repair_status_history", "repair_quotes", "repair_part_usages", "repair_part_returns", "service_requests",
			"service_request_events", "service_visits", "technician_shifts", "branch_service_settings",
			"notification_outbox", "notification_attempts", "audit_events");

	/** ER-BR-001 section 7, written independently of RepairPolicy. */
	private static final List<String> ALLOWED_TRANSITIONS = List.of("RECEIVED>DIAGNOSING", "RECEIVED>CANCELLED",
			"DIAGNOSING>AWAITING_APPROVAL", "DIAGNOSING>IN_REPAIR", "DIAGNOSING>UNREPAIRABLE", "DIAGNOSING>CANCELLED",
			"AWAITING_APPROVAL>APPROVED", "AWAITING_APPROVAL>CANCELLED", "APPROVED>IN_REPAIR", "APPROVED>CANCELLED",
			"IN_REPAIR>READY_FOR_PICKUP", "IN_REPAIR>AWAITING_APPROVAL", "IN_REPAIR>UNREPAIRABLE",
			"READY_FOR_PICKUP>DELIVERED", "CANCELLED>DELIVERED", "UNREPAIRABLE>DELIVERED");

	@Autowired
	private DemoDataSeeder seeder;

	@Autowired
	private DashboardService dashboard;

	@Autowired
	private UserDetailsService accounts;

	@AfterEach
	void clearSecurity() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void skipsWhenTheBranchesAndPeopleAreMissing() {
		assertThat(seeder.seed()).isEqualTo(DemoDataSeeder.Result.SKIPPED);
		assertThat(countRows("products")).isZero();
		assertThat(countRows("audit_events")).isZero();
	}

	@Test
	void loadsTheScenarioThroughTheUseCasesAndIsIdempotent() {
		Map<String, Long> people = preparePeople();
		Instant before = Instant.now();

		assertThat(seeder.seed()).isEqualTo(DemoDataSeeder.Result.LOADED);
		Map<String, Integer> afterFirst = counts();

		// Reused, never duplicated.
		assertThat(afterFirst.get("branches")).isEqualTo(2);
		assertThat(afterFirst.get("app_users")).isEqualTo(6);
		assertThat(afterFirst.get("products")).isEqualTo(DemoCatalog.ITEMS.size());
		assertThat(afterFirst.get("customers")).isEqualTo(30);
		assertThat(afterFirst.get("stock_transfers")).isEqualTo(9);
		assertThat(afterFirst.get("repair_orders")).isBetween(20, 25);
		assertThat(afterFirst.get("service_visits")).isBetween(12, 15);

		// Second run: nothing changes.
		assertThat(seeder.seed()).isEqualTo(DemoDataSeeder.Result.ALREADY_LOADED);
		assertThat(counts()).isEqualTo(afterFirst);

		assertStockLedgerIsConsistent();
		assertRepairHistoriesFollowTheStateMachine();
		assertNoTechnicianIsDoubleBooked();
		assertTransfersAndPartsHaveTheirMovements();
		assertAuditComesFromTheOperations(people);
		assertNoticesStayedInTheDevelopmentInbox();
		assertDataIsFictitiousAndInThePast(before);
		assertDashboardShowsTheScenario(people.get("Jefferson Rojas"));
	}

	private void assertStockLedgerIsConsistent() {
		assertThat(scalar("SELECT count(*) FROM branch_stock WHERE quantity < 0")).isZero();
		assertThat(scalar("""
				SELECT count(*) FROM branch_stock s WHERE s.quantity <> (SELECT coalesce(sum(m.quantity_delta), 0)
				    FROM stock_movements m WHERE m.branch_id = s.branch_id AND m.product_id = s.product_id)
				""")).isZero();
		assertThat(scalar("""
				SELECT count(*) FROM (SELECT balance_after, quantity_delta,
				    lag(balance_after, 1, 0) OVER (PARTITION BY branch_id, product_id ORDER BY id) AS previous
				    FROM stock_movements) m WHERE m.previous + m.quantity_delta <> m.balance_after
				""")).isZero();
		// Each branch shows attention states, as planned: one article out of stock, three below the minimum.
		for (String code : List.of("SUC-001", "SUC-002")) {
			assertThat(scalar("SELECT count(*) FROM branch_stock s JOIN branches b ON b.id = s.branch_id "
					+ "WHERE b.code = ? AND s.quantity = 0", code)).isEqualTo(1);
			assertThat(scalar("SELECT count(*) FROM branch_stock s JOIN branches b ON b.id = s.branch_id "
					+ "WHERE b.code = ? AND s.quantity > 0 AND s.minimum_quantity > s.quantity", code)).isEqualTo(3);
		}
	}

	private void assertRepairHistoriesFollowTheStateMachine() {
		List<String> pairs = jdbc.queryForList(
				"SELECT DISTINCT from_status || '>' || to_status FROM repair_status_history WHERE from_status IS NOT NULL",
				String.class);
		assertThat(ALLOWED_TRANSITIONS).containsAll(pairs);
		assertThat(scalar("""
				SELECT count(*) FROM (SELECT from_status, to_status,
				    lag(to_status) OVER (PARTITION BY order_id ORDER BY changed_at, id) AS previous
				    FROM repair_status_history) h WHERE h.from_status IS DISTINCT FROM h.previous
				""")).isZero();
		assertThat(scalar("""
				SELECT count(*) FROM repair_orders o WHERE o.status <> (SELECT h.to_status FROM repair_status_history h
				    WHERE h.order_id = o.id ORDER BY h.changed_at DESC, h.id DESC LIMIT 1)
				""")).isZero();
		// Several stages at once, as a working shop has.
		assertThat(scalar("SELECT count(DISTINCT status) FROM repair_orders")).isGreaterThanOrEqualTo(7);
		assertThat(scalar("SELECT count(*) FROM repair_quotes WHERE status = 'REJECTED'")).isEqualTo(2);
	}

	private void assertNoTechnicianIsDoubleBooked() {
		assertThat(scalar("""
				SELECT count(*) FROM service_visits a JOIN service_visits b
				    ON a.technician_id = b.technician_id AND a.id < b.id
				   AND tstzrange(a.scheduled_start, a.blocked_until) && tstzrange(b.scheduled_start, b.blocked_until)
				WHERE a.status <> 'CANCELLED' AND b.status <> 'CANCELLED'
				""")).isZero();
		assertThat(scalar("SELECT count(*) FROM service_visits WHERE status = 'CANCELLED'")).isEqualTo(1);
		assertThat(scalar("""
				SELECT count(*) FROM service_request_events WHERE event_type = 'VISIT_RESCHEDULED'
				""")).isEqualTo(1);
	}

	private void assertTransfersAndPartsHaveTheirMovements() {
		assertThat(scalar("""
				SELECT count(*) FROM stock_transfers t WHERE 2 <> (SELECT count(*) FROM stock_movements m
				    WHERE m.transfer_id = t.id AND abs(m.quantity_delta) = t.quantity)
				   OR 0 <> (SELECT sum(m.quantity_delta) FROM stock_movements m WHERE m.transfer_id = t.id)
				""")).isZero();
		assertThat(scalar("SELECT count(DISTINCT order_id) FROM repair_part_usages")).isGreaterThanOrEqualTo(10);
		assertThat(scalar("""
				SELECT count(*) FROM repair_part_usages u JOIN stock_movements m ON m.id = u.movement_id
				WHERE m.type <> 'OUT_FOR_REPAIR' OR m.repair_order_id <> u.order_id OR m.quantity_delta <> -u.quantity
				""")).isZero();
		assertThat(scalar("""
				SELECT count(*) FROM repair_part_returns r JOIN stock_movements m ON m.id = r.movement_id
				WHERE m.type = 'RETURN_FROM_REPAIR' AND m.quantity_delta = r.quantity
				""")).isEqualTo(3);
	}

	private void assertAuditComesFromTheOperations(Map<String, Long> people) {
		assertThat(scalar("SELECT count(*) FROM audit_events WHERE action = 'REPAIR_ORDER_RECEIVED'"))
			.isEqualTo(scalar("SELECT count(*) FROM repair_orders"));
		assertThat(scalar("SELECT count(*) FROM audit_events WHERE action = 'REPAIR_STATUS_CHANGED'"))
			.isEqualTo(scalar("SELECT count(*) FROM repair_status_history WHERE from_status IS NOT NULL"));
		assertThat(scalar("SELECT count(*) FROM audit_events WHERE action = 'STOCK_TRANSFER_COMPLETED'"))
			.isEqualTo(2 * scalar("SELECT count(*) FROM stock_transfers"));
		assertThat(scalar("SELECT count(*) FROM audit_events WHERE action = 'STOCK_MOVEMENT_RECORDED'"))
			.isEqualTo(scalar("SELECT count(*) FROM stock_movements WHERE transfer_id IS NULL AND repair_order_id IS NULL"));
		assertThat(scalar("SELECT count(*) FROM audit_events WHERE action = 'REPAIR_PART_CONSUMED'"))
			.isEqualTo(scalar("SELECT count(*) FROM repair_part_usages"));
		// Every actor is one of the reused people; only public-form submissions have none.
		List<Long> actors = jdbc.queryForList("SELECT DISTINCT actor_id FROM audit_events WHERE actor_id IS NOT NULL",
				Long.class);
		assertThat(people.values()).containsAll(actors);
		assertThat(people.get("Administrador")).isNotIn(actors);
		assertThat(scalar("SELECT count(*) FROM audit_events WHERE actor_id IS NULL AND action <> 'SERVICE_REQUEST_SUBMITTED'"))
			.isZero();
		assertThat(scalar("SELECT count(DISTINCT action) FROM audit_events")).isGreaterThanOrEqualTo(20);
	}

	private void assertNoticesStayedInTheDevelopmentInbox() {
		assertThat(scalar("SELECT count(*) FROM notification_outbox WHERE status NOT IN ('SENT', 'SKIPPED')")).isZero();
		assertThat(scalar("SELECT count(*) FROM notification_outbox WHERE status = 'SENT'")).isGreaterThanOrEqualTo(15);
		assertThat(scalar("SELECT count(*) FROM notification_outbox WHERE status = 'SKIPPED'")).isGreaterThan(0);
		assertThat(scalar("""
				SELECT count(*) FROM notification_outbox o JOIN customers c ON c.id = o.customer_id
				WHERE o.status = 'SENT' AND c.email NOT LIKE '%@example.test'
				""")).isZero();
		assertThat(scalar("SELECT count(*) FROM notification_outbox WHERE sent_at < created_at")).isZero();
	}

	private void assertDataIsFictitiousAndInThePast(Instant before) {
		assertThat(scalar("SELECT count(*) FROM customers WHERE email IS NOT NULL AND email NOT LIKE '%@example.test'"))
			.isZero();
		assertThat(scalar("SELECT count(*) FROM customers WHERE phone NOT LIKE '+5060555%'")).isZero();
		assertThat(scalar("""
				SELECT count(*) FROM (
				    SELECT reported_fault || ' ' || physical_condition || ' ' || coalesce(diagnosis, '') AS text FROM repair_orders
				    UNION ALL SELECT coalesce(reason, '') FROM stock_movements
				    UNION ALL SELECT name FROM products
				    UNION ALL SELECT problem_description || ' ' || address_line FROM service_requests
				    UNION ALL SELECT full_name FROM customers
				    UNION ALL SELECT description FROM repair_quotes) t
				WHERE t.text ~* '(demo|seed|test|asdf|lorem)'
				""")).isZero();
		Instant now = Instant.now();
		assertThat(jdbc.queryForObject("SELECT max(occurred_at) FROM audit_events", java.sql.Timestamp.class).toInstant())
			.isBefore(now);
		assertThat(jdbc.queryForObject("SELECT min(occurred_at) FROM audit_events", java.sql.Timestamp.class).toInstant())
			.isAfter(before.minus(80, ChronoUnit.DAYS))
			.isBefore(before.minus(55, ChronoUnit.DAYS));
	}

	private void assertDashboardShowsTheScenario(long adminId) {
		CurrentUser admin = (CurrentUser) accounts.loadUserByUsername(
				jdbc.queryForObject("SELECT email FROM app_users WHERE id = ?", String.class, adminId));
		SecurityContextHolder.getContext()
			.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.getAuthorities()));
		DashboardResponse response = dashboard.dashboard(admin, null);
		Map<String, Long> indicators = new LinkedHashMap<>();
		for (Indicator indicator : response.indicators()) {
			indicators.put(indicator.key(), indicator.count());
		}
		assertThat(indicators).containsEntry("STOCK_OUT", 2L).containsEntry("STOCK_LOW", 6L)
			.containsEntry("REPAIRS_READY", 3L);
	}

	/** The branches and collaborators that already exist in the owner's database. */
	private Map<String, Long> preparePeople() {
		long tresRios = jdbc.queryForObject("""
				INSERT INTO branches (code, name, address, active, created_at, updated_at)
				VALUES ('SUC-001', 'Sucursal Tres Ríos', NULL, TRUE, now(), now()) RETURNING id
				""", Long.class);
		long sanPedro = jdbc.queryForObject("""
				INSERT INTO branches (code, name, address, active, created_at, updated_at)
				VALUES ('SUC-002', 'Sucursal San Pedro', NULL, TRUE, now(), now()) RETURNING id
				""", Long.class);
		Map<String, Long> people = new LinkedHashMap<>();
		people.put("Administrador", jdbc.queryForObject("SELECT id FROM app_users WHERE email = ?", Long.class,
				ADMIN_EMAIL));
		people.put("Jefferson Rojas", person("jefferson@electronica-rojas.test", "Jefferson Rojas", Role.ADMIN));
		people.put("Julián Alvarez", person("julian@electronica-rojas.test", "Julián Alvarez", Role.TECHNICIAN, tresRios));
		people.put("Pedro Fernandez", person("pedro@electronica-rojas.test", "Pedro Fernandez", Role.TECHNICIAN, sanPedro));
		people.put("Susan Rojas", person("susan@electronica-rojas.test", "Susan Rojas", Role.RECEPTIONIST, tresRios));
		people.put("Maicol Armas", person("maicol@electronica-rojas.test", "Maicol Armas", Role.RECEPTIONIST, sanPedro));
		return people;
	}

	private long person(String email, String fullName, Role role, long... branchIds) {
		long id = createUser(email, role, branchIds);
		jdbc.update("UPDATE app_users SET full_name = ? WHERE id = ?", fullName, id);
		return id;
	}

	private Map<String, Integer> counts() {
		Map<String, Integer> counts = new LinkedHashMap<>();
		TABLES.forEach(table -> counts.put(table, countRows(table)));
		return counts;
	}

	private long scalar(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

}
