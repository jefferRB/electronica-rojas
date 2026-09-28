package dev.jeffrojas.electronicarojas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import dev.jeffrojas.electronicarojas.security.LoginAttemptLimiter;
import dev.jeffrojas.electronicarojas.security.Role;
import dev.jeffrojas.electronicarojas.servicerequests.PublicRequestLimiter;

/**
 * Base class for integration tests. All subclasses share ONE Spring context and ONE
 * PostgreSQL 17 container (identical configuration = cached context), and start every test
 * from a clean dataset containing only the bootstrap administrator.
 * <p>
 * All credentials here are fictitious test values, valid only inside the throwaway container.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"app.bootstrap.admin.email=" + IntegrationTestSupport.ADMIN_EMAIL,
		"app.bootstrap.admin.password=" + IntegrationTestSupport.ADMIN_PASSWORD,
		// The notification worker runs only when a test calls it, so outbox tests are deterministic.
		"app.notifications.worker-enabled=false" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestSupport {

	public static final String ADMIN_EMAIL = "admin@electronica-rojas.test";

	public static final String ADMIN_PASSWORD = "test-admin-password-1";

	public static final String USER_PASSWORD = "test-user-password-1";

	private static String userPasswordHash;

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected PasswordEncoder passwordEncoder;

	@Autowired
	private LoginAttemptLimiter loginAttempts;

	@Autowired
	private PublicRequestLimiter publicRequests;

	@BeforeEach
	void resetData() {
		// Every test logs in from 127.0.0.1; failures of one test must not throttle the next.
		loginAttempts.reset();
		publicRequests.reset();
		// History tables are append-only (DELETE is rejected by a trigger); TRUNCATE is the
		// test-only way to empty them.
		jdbc.execute("""
				TRUNCATE audit_events, notification_attempts, notification_outbox, customer_consents, repair_part_returns, repair_part_usages, stock_movements, stock_transfers, branch_stock, products, service_request_events,
					service_visits, service_requests, technician_shifts, branch_service_settings, repair_quotes,
					repair_status_history, repair_orders, customers, public_portal_slug_history RESTART IDENTITY""");
		// The portal is a single row created by V12: back to its initial settings.
		jdbc.update("""
				UPDATE public_portal_settings SET enabled = TRUE, slug = 'servicio-a-domicilio', allow_preferred_date = TRUE,
					allow_preferred_window = TRUE, min_notice_days = 0, max_days_ahead = 90,
					service_days = '[1, 2, 3, 4, 5, 6, 7]',
					served_provinces = '["SAN_JOSE", "ALAJUELA", "CARTAGO", "HEREDIA", "GUANACASTE", "PUNTARENAS", "LIMON"]',
					service_types = '[]', welcome_message = NULL, success_message = NULL, updated_by = NULL, version = 0""");
		jdbc.update("DELETE FROM user_branches");
		jdbc.update("DELETE FROM app_users WHERE email <> ?", ADMIN_EMAIL);
		jdbc.update("DELETE FROM branches");
		jdbc.update("UPDATE app_users SET role = 'ADMIN', active = TRUE WHERE email = ?", ADMIN_EMAIL);
		if (userPasswordHash == null) {
			userPasswordHash = passwordEncoder.encode(USER_PASSWORD);
		}
	}

	protected long createBranch(String code) {
		return createBranch(code, true);
	}

	protected long createBranch(String code, boolean active) {
		return jdbc.queryForObject("""
				INSERT INTO branches (code, name, address, active, created_at, updated_at)
				VALUES (?, ?, NULL, ?, now(), now()) RETURNING id
				""", Long.class, code, "Sucursal " + code, active);
	}

	/** Creates an account whose password is {@link #USER_PASSWORD}. */
	protected long createUser(String email, Role role, long... branchIds) {
		long userId = jdbc.queryForObject("""
				INSERT INTO app_users (email, full_name, password_hash, role, active, created_at, updated_at)
				VALUES (?, ?, ?, ?, TRUE, now(), now()) RETURNING id
				""", Long.class, email, "Usuario " + role, userPasswordHash, role.name());
		for (long branchId : branchIds) {
			jdbc.update("INSERT INTO user_branches (user_id, branch_id) VALUES (?, ?)", userId, branchId);
		}
		return userId;
	}

	/**
	 * Valid CSRF proof exactly as the SPA sends it (double-submit): the same random value in the
	 * XSRF-TOKEN cookie and the X-XSRF-TOKEN header. A cross-site page can do neither.
	 * <p>
	 * Deliberately not Spring Security Test's {@code csrf()}: that post-processor permanently
	 * replaces the CsrfFilter's cookie repository in the shared context with a session-based
	 * test repository, which would silently change what later tests exercise.
	 */
	protected static RequestPostProcessor xsrf() {
		String token = UUID.randomUUID().toString();
		return request -> {
			List<Cookie> cookies = new ArrayList<>();
			if (request.getCookies() != null) {
				cookies.addAll(List.of(request.getCookies()));
			}
			cookies.add(new Cookie("XSRF-TOKEN", token));
			request.setCookies(cookies.toArray(Cookie[]::new));
			request.addHeader("X-XSRF-TOKEN", token);
			return request;
		};
	}

	/** Active spare part in category "Repuestos" named "Producto {sku}". */
	protected long createProduct(String sku) {
		return createProduct(sku, true);
	}

	protected long createProduct(String sku, boolean active) {
		return jdbc.queryForObject("""
				INSERT INTO products (sku, name, category, kind, active, created_at, updated_at)
				VALUES (?, ?, 'Repuestos', 'SPARE_PART', ?, now(), now()) RETURNING id
				""", Long.class, sku, "Producto " + sku, active);
	}

	/** Test fixture only: real code changes stock exclusively through movements. */
	protected void setStock(long branchId, long productId, int quantity) {
		jdbc.update("""
				INSERT INTO branch_stock (branch_id, product_id, quantity, minimum_quantity, updated_at)
				VALUES (?, ?, ?, 0, now())
				ON CONFLICT (branch_id, product_id) DO UPDATE SET quantity = EXCLUDED.quantity
				""", branchId, productId, quantity);
	}

	/** Current stock, 0 when the branch never had the product. */
	protected int stockOf(long branchId, long productId) {
		Integer quantity = jdbc.query("SELECT quantity FROM branch_stock WHERE branch_id = ? AND product_id = ?",
				rs -> rs.next() ? rs.getInt(1) : null, branchId, productId);
		return quantity == null ? 0 : quantity;
	}

	protected int countRows(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}

	protected MockHttpSession loginAsAdmin() throws Exception {
		return login(ADMIN_EMAIL, ADMIN_PASSWORD);
	}

	protected MockHttpSession login(String email) throws Exception {
		return login(email, USER_PASSWORD);
	}

	/** Real form login through the security filter chain; returns the authenticated session. */
	protected MockHttpSession login(String email, String password) throws Exception {
		return (MockHttpSession) mockMvc
			.perform(post("/api/v1/auth/login").with(xsrf()).param("email", email).param("password", password))
			.andExpect(status().isNoContent())
			.andReturn()
			.getRequest()
			.getSession(false);
	}

}
