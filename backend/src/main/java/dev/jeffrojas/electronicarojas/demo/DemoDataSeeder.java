package dev.jeffrojas.electronicarojas.demo;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jeffrojas.electronicarojas.notifications.NotificationDispatcher;
import dev.jeffrojas.electronicarojas.servicerequests.PublicRequestLimiter;

/**
 * DEV-ONLY loader of the demo scenario ({@link DemoScenario}). Exists only with
 * {@code app.demo.seed=true} (environment variable {@code APP_DEMO_SEED}); off by default.
 * <ul>
 * <li>Runs once at startup, before the web server accepts requests and before scheduled tasks
 * start, so no real request ever sees the simulated clock.</li>
 * <li>Refuses a non-local database or a real mail transport.</li>
 * <li>Idempotent: if the demo catalog is already there it does nothing. It never deletes or edits
 * existing data, and only loads into a database without prior operations.</li>
 * <li>All-or-nothing: the scenario runs in one transaction; a failed operation leaves the database
 * as it was. Customer notices then leave the outbox through the real worker, at the instants of
 * the scenario, into the development inbox.</li>
 * </ul>
 */
class DemoDataSeeder implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

	/** The worker polls every few seconds: a notice leaves shortly after its event. */
	private static final Duration WORKER_DELAY = Duration.ofSeconds(25);

	enum Result {
		LOADED, ALREADY_LOADED, SKIPPED
	}

	private final DemoServices services;

	private final DemoClock clock;

	private final JdbcClient jdbc;

	private final UserDetailsService accounts;

	private final TransactionTemplate transaction;

	private final EntityManager entityManager;

	private final NotificationDispatcher dispatcher;

	private final PublicRequestLimiter publicLimiter;

	private final DataSource dataSource;

	private final Environment environment;

	private volatile boolean running;

	DemoDataSeeder(DemoServices services, DemoClock clock, JdbcClient jdbc, UserDetailsService accounts,
			TransactionTemplate transaction, EntityManager entityManager, NotificationDispatcher dispatcher,
			PublicRequestLimiter publicLimiter, DataSource dataSource, Environment environment) {
		this.services = services;
		this.clock = clock;
		this.jdbc = jdbc;
		this.accounts = accounts;
		this.transaction = transaction;
		this.entityManager = entityManager;
		this.dispatcher = dispatcher;
		this.publicLimiter = publicLimiter;
		this.dataSource = dataSource;
		this.environment = environment;
	}

	@Override
	public void start() {
		running = true;
		seed();
	}

	@Override
	public void stop() {
		running = false;
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	/** Just before the embedded web server starts (scheduled tasks start after every lifecycle bean). */
	@Override
	public int getPhase() {
		return WebServerApplicationContext.START_STOP_LIFECYCLE_PHASE - 1;
	}

	synchronized Result seed() {
		requireLocalDatabase();
		requireDevelopmentInbox();
		List<String> skus = DemoCatalog.skus();
		long present = jdbc.sql("SELECT count(*) FROM products WHERE sku IN (:skus)").param("skus", skus)
			.query(Long.class)
			.single();
		if (present == skus.size()) {
			log.info("Datos demo: ya están cargados; no se modifica nada");
			return Result.ALREADY_LOADED;
		}
		if (present > 0) {
			log.warn("Datos demo: la base tiene {} de {} artículos del escenario; no se carga nada. "
					+ "Restaure la base local (docs/runbook.md, «Datos demo para el portafolio»)", present, skus.size());
			return Result.SKIPPED;
		}
		long operations = jdbc.sql("""
				SELECT (SELECT count(*) FROM products) + (SELECT count(*) FROM customers)
				     + (SELECT count(*) FROM repair_orders) + (SELECT count(*) FROM service_requests)
				     + (SELECT count(*) FROM technician_shifts)
				""").query(Long.class).single();
		if (operations > 0) {
			log.warn("Datos demo: la base ya tiene operaciones propias; el escenario solo se carga sobre una base sin "
					+ "operaciones y nunca borra datos. No se carga nada");
			return Result.SKIPPED;
		}
		DemoActors actors = DemoActors.resolve(jdbc, accounts);
		if (!actors.complete()) {
			log.warn("Datos demo: faltan en la base {}; no se carga nada", actors.missing());
			return Result.SKIPPED;
		}

		Instant now = clock.instant();
		DemoTimeline timeline = new DemoScenario(services, actors, new DemoCalendar(now)).build();
		// Operations of later today (and future visits) are not replayed: they have not happened yet.
		Instant notAfter = now.minus(Duration.ofMinutes(1));
		try {
			log.info("Datos demo: reproduciendo {} operaciones del escenario...", timeline.size());
			DemoTimeline.Outcome outcome = transaction.execute(status -> timeline.run(notAfter, clock, entityManager));
			int notices = deliverNotices();
			log.info("Datos demo cargados: {} operaciones ({} aún no ocurren), {} avisos procesados por la bandeja de "
					+ "salida. {}", outcome.executed(), outcome.pending(), notices, summary());
			return Result.LOADED;
		}
		finally {
			clock.resume();
			SecurityContextHolder.clearContext();
			// The scenario's public submissions must not count against real ones.
			publicLimiter.reset();
		}
	}

	/**
	 * The outbox worker as it would have run: shortly after each notice was stored. Same code as the
	 * scheduled worker (claim, consent re-check, compose, transport, fenced result).
	 */
	private int deliverNotices() {
		List<Instant> stored = jdbc.sql("""
				SELECT DISTINCT created_at FROM notification_outbox WHERE status = 'PENDING' ORDER BY created_at
				""").query((rs, row) -> rs.getTimestamp(1).toInstant()).list();
		int processed = 0;
		for (Instant createdAt : stored) {
			clock.travelTo(createdAt.plus(WORKER_DELAY));
			int batch;
			do {
				batch = dispatcher.runOnce();
				processed += batch;
			}
			while (batch > 0);
		}
		return processed;
	}

	private String summary() {
		return jdbc.sql("""
				SELECT 'clientes=' || (SELECT count(*) FROM customers)
				    || ', artículos=' || (SELECT count(*) FROM products)
				    || ', movimientos=' || (SELECT count(*) FROM stock_movements)
				    || ', transferencias=' || (SELECT count(*) FROM stock_transfers)
				    || ', reparaciones=' || (SELECT count(*) FROM repair_orders)
				    || ', solicitudes=' || (SELECT count(*) FROM service_requests)
				    || ', visitas=' || (SELECT count(*) FROM service_visits)
				    || ', avisos=' || (SELECT count(*) FROM notification_outbox)
				    || ', auditoría=' || (SELECT count(*) FROM audit_events)
				""").query(String.class).single();
	}

	/** Fail closed: demo data is only ever written to a database on this machine. */
	private void requireLocalDatabase() {
		String url;
		try (Connection connection = dataSource.getConnection()) {
			url = connection.getMetaData().getURL();
		}
		catch (SQLException ex) {
			throw new IllegalStateException("Datos demo: no se pudo leer la conexión a la base", ex);
		}
		String host = url.startsWith("jdbc:") ? URI.create(url.substring("jdbc:".length())).getHost() : null;
		if (host == null || !LOCAL_HOSTS.contains(host)) {
			throw new IllegalStateException("APP_DEMO_SEED=true solo se admite contra una base local (localhost); "
					+ "desactívelo para este entorno");
		}
	}

	/** Fail closed: with a real mail provider the scenario's notices could leave the machine. */
	private void requireDevelopmentInbox() {
		String mode = environment.getProperty("app.notifications.mail.mode", "inbox");
		if (!"inbox".equals(mode)) {
			throw new IllegalStateException("APP_DEMO_SEED=true requiere MAIL_MODE=inbox (bandeja de desarrollo)");
		}
	}

}
