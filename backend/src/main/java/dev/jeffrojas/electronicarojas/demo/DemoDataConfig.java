package dev.jeffrojas.electronicarojas.demo;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jeffrojas.electronicarojas.customers.CustomerConsentService;
import dev.jeffrojas.electronicarojas.customers.CustomerService;
import dev.jeffrojas.electronicarojas.inventory.BranchStockService;
import dev.jeffrojas.electronicarojas.inventory.ProductCatalogService;
import dev.jeffrojas.electronicarojas.inventory.StockMovementService;
import dev.jeffrojas.electronicarojas.inventory.StockTransferService;
import dev.jeffrojas.electronicarojas.notifications.NotificationDispatcher;
import dev.jeffrojas.electronicarojas.repairs.RepairOrderService;
import dev.jeffrojas.electronicarojas.repairs.RepairPartService;
import dev.jeffrojas.electronicarojas.repairs.RepairQuoteService;
import dev.jeffrojas.electronicarojas.servicerequests.PortalSettingsService;
import dev.jeffrojas.electronicarojas.servicerequests.PublicRequestLimiter;
import dev.jeffrojas.electronicarojas.servicerequests.PublicServiceRequestService;
import dev.jeffrojas.electronicarojas.servicerequests.ScheduleConfigService;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceRequestService;
import dev.jeffrojas.electronicarojas.servicerequests.VisitService;

/**
 * DEV-ONLY demo data (docs/runbook.md, «Datos demo para el portafolio»). None of these beans exist
 * unless {@code app.demo.seed=true}; without it the application is exactly the production one.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
class DemoDataConfig {

	/** Replaces the application clock only in this mode; it reads real time except while loading. */
	@Bean
	@Primary
	DemoClock demoClock() {
		return new DemoClock();
	}

	@Bean
	DemoServices demoServices(ProductCatalogService catalog, BranchStockService stock, StockMovementService movements,
			StockTransferService transfers, CustomerService customers, CustomerConsentService consents,
			RepairOrderService repairs, RepairQuoteService quotes, RepairPartService parts,
			ServiceRequestService requests, PublicServiceRequestService publicRequests, VisitService visits,
			ScheduleConfigService schedules, PortalSettingsService portal, Validator validator) {
		return new DemoServices(catalog, stock, movements, transfers, customers, consents, repairs, quotes, parts,
				requests, publicRequests, visits, schedules, portal, validator);
	}

	@Bean
	DemoDataSeeder demoDataSeeder(DemoServices services, DemoClock clock, JdbcClient jdbc, UserDetailsService accounts,
			PlatformTransactionManager transactionManager, EntityManager entityManager,
			NotificationDispatcher dispatcher, PublicRequestLimiter publicLimiter, DataSource dataSource,
			Environment environment) {
		return new DemoDataSeeder(services, clock, jdbc, accounts, new TransactionTemplate(transactionManager),
				entityManager, dispatcher, publicLimiter, dataSource, environment);
	}

}
