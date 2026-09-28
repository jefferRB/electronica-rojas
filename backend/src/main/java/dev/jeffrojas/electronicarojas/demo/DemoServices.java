package dev.jeffrojas.electronicarojas.demo;

import jakarta.validation.Validator;

import dev.jeffrojas.electronicarojas.customers.CustomerConsentService;
import dev.jeffrojas.electronicarojas.customers.CustomerService;
import dev.jeffrojas.electronicarojas.inventory.BranchStockService;
import dev.jeffrojas.electronicarojas.inventory.ProductCatalogService;
import dev.jeffrojas.electronicarojas.inventory.StockMovementService;
import dev.jeffrojas.electronicarojas.inventory.StockTransferService;
import dev.jeffrojas.electronicarojas.repairs.RepairOrderService;
import dev.jeffrojas.electronicarojas.repairs.RepairPartService;
import dev.jeffrojas.electronicarojas.repairs.RepairQuoteService;
import dev.jeffrojas.electronicarojas.servicerequests.PortalSettingsService;
import dev.jeffrojas.electronicarojas.servicerequests.PublicServiceRequestService;
import dev.jeffrojas.electronicarojas.servicerequests.ScheduleConfigService;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceRequestService;
import dev.jeffrojas.electronicarojas.servicerequests.VisitService;

/**
 * The public use cases the demo scenario goes through: the same ones the REST controllers call.
 * {@code validator} applies the request DTOs' Jakarta constraints, as the controllers do with
 * {@code @Valid}.
 */
record DemoServices(ProductCatalogService catalog, BranchStockService stock, StockMovementService movements,
		StockTransferService transfers, CustomerService customers, CustomerConsentService consents,
		RepairOrderService repairs, RepairQuoteService quotes, RepairPartService parts,
		ServiceRequestService requests, PublicServiceRequestService publicRequests, VisitService visits,
		ScheduleConfigService schedules, PortalSettingsService portal, Validator validator) {
}
