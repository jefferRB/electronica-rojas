package dev.jeffrojas.electronicarojas.repairs;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.customers.Customer;
import dev.jeffrojas.electronicarojas.inventory.InventoryDtos.ProductSummary;
import dev.jeffrojas.electronicarojas.inventory.PricingPolicy;
import dev.jeffrojas.electronicarojas.inventory.Product;
import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SubjectType;
import dev.jeffrojas.electronicarojas.notifications.NotificationOutbox;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.AvailableTransition;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.OrderActions;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.OrderCustomer;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.PartReturn;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.PartUsage;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.PartsSummary;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.PersonSummary;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.Quote;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.RepairOrderDetail;
import dev.jeffrojas.electronicarojas.repairs.RepairDtos.StatusChange;
import dev.jeffrojas.electronicarojas.repairs.RepairEnums.QuoteStatus;
import dev.jeffrojas.electronicarojas.security.CurrentUser;

/**
 * Builds the order detail DTO: data, timeline, quotes, spare parts (with their pricing snapshot and
 * totals) and the actions available to the caller.
 */
@Component
class RepairDetailAssembler {

	private final RepairStatusChangeRepository history;

	private final RepairQuoteRepository quotes;

	private final RepairPartUsageRepository partUsages;

	private final RepairPartReturnRepository partReturns;

	private final NotificationOutbox notifications;

	RepairDetailAssembler(RepairStatusChangeRepository history, RepairQuoteRepository quotes,
			RepairPartUsageRepository partUsages, RepairPartReturnRepository partReturns,
			NotificationOutbox notifications) {
		this.history = history;
		this.quotes = quotes;
		this.partUsages = partUsages;
		this.partReturns = partReturns;
		this.notifications = notifications;
	}

	RepairOrderDetail detail(CurrentUser user, RepairOrder order) {
		var quoteList = quotes.findForOrder(order.getId());
		boolean pendingQuote = quoteList.stream().anyMatch(quote -> quote.getStatus() == QuoteStatus.PENDING);
		boolean showCost = PricingPolicy.mayViewCost(user);
		List<RepairPartUsage> usages = partUsages.findForOrder(order.getId());
		List<PartUsage> parts = parts(order.getId(), usages, showCost);
		PartCharges.Summary totals = PartCharges.summarize(usages.stream().map(RepairPartUsage::charges).toList(),
				showCost);
		PartsSummary partsSummary = new PartsSummary(totals.linesInUse(), totals.unitsInUse(),
				totals.unitsWithoutCharge(), totals.chargeableSubtotal(), totals.unpricedLines(), totals.totalCost(),
				totals.uncostedLines(), "CRC");
		OrderActions actions = new OrderActions(
				RepairPolicy.availableTransitions(user, order)
					.stream()
					.map(to -> new AvailableTransition(to, RepairPolicy.requiresReason(to)))
					.toList(),
				RepairPolicy.mayAssignTechnician(user, order), RepairPolicy.mayEditDiagnosis(user, order),
				RepairPolicy.mayCreateQuote(user, order) && !pendingQuote,
				RepairPolicy.mayDecideQuote(user, order) && pendingQuote, RepairPolicy.mayConsumeParts(user, order),
				RepairPolicy.mayReturnParts(user, order) && parts.stream().anyMatch(part -> part.remainingQuantity() > 0),
				RepairPolicy.mayConsumeParts(user, order) && RepairPolicy.mayOverridePartPricing(user));

		Customer customer = order.getCustomer();
		boolean contact = RepairPolicy.mayViewCustomerContact(user);
		return new RepairOrderDetail(order.getId(), order.getOrderCode(), order.getStatus(), order.getResolution(),
				order.isInCustody(), BranchSummary.from(order.getBranch()),
				new OrderCustomer(customer.getId(), customer.getFullName(), contact ? customer.getPhone() : null,
						contact ? customer.getEmail() : null),
				RepairDtos.device(order), order.getReportedFault(), order.getPhysicalCondition(), order.getAccessories(),
				PersonSummary.of(order.getAssignedTechnician()), order.getDiagnosis(), order.getDiagnosisUpdatedAt(),
				PersonSummary.of(order.getDiagnosisUpdatedBy()), order.getReceivedAt(),
				PersonSummary.of(order.getReceivedBy()), order.getDeliveredAt(), PersonSummary.of(order.getDeliveredBy()),
				order.getVersion(),
				history.findTimeline(order.getId())
					.stream()
					.map(change -> new StatusChange(change.getFromStatus(), change.getToStatus(), change.getReason(),
							PersonSummary.of(change.getActor()), change.getChangedAt()))
					.toList(),
				quoteList.stream()
					.map(quote -> new Quote(quote.getId(), quote.getAmount(), quote.getCurrency(), quote.getDescription(),
							quote.getStatus(), PersonSummary.of(quote.getCreatedBy()), quote.getCreatedAt(),
							PersonSummary.of(quote.getDecidedBy()), quote.getDecidedAt(), quote.getDecisionMethod(),
							quote.getDecisionNote()))
					.toList(),
				parts, partsSummary, notifications.statusFor(SubjectType.REPAIR_ORDER, List.of(order.getId())), actions);
	}

	/** Two queries per order (lines, then all their returns), grouped in memory: no N+1. */
	private List<PartUsage> parts(long orderId, List<RepairPartUsage> usages, boolean showCost) {
		Map<Long, List<PartReturn>> returnsByUsage = partReturns.findForOrder(orderId)
			.stream()
			.collect(Collectors.groupingBy(ret -> ret.getUsage().getId(),
					Collectors.mapping(ret -> new PartReturn(ret.getId(), ret.getQuantity(), ret.getReason(),
							ret.getOrderStatus(), PersonSummary.of(ret.getRecordedBy()), ret.getRecordedAt()),
							Collectors.toList())));
		return usages.stream()
			.map(usage -> new PartUsage(usage.getId(), product(usage.getProduct()), BranchSummary.from(usage.getBranch()),
					usage.getQuantity(), usage.getReturnedQuantity(), usage.remainingQuantity(), usage.getUnitPrice(),
					showCost ? usage.getUnitCost() : null, usage.isChargeable(), usage.isPriceOverridden(),
					PartCharges.chargedAmount(usage.charges()), usage.getNote(), PersonSummary.of(usage.getRecordedBy()),
					usage.getRecordedAt(), returnsByUsage.getOrDefault(usage.getId(), List.of())))
			.toList();
	}

	private static ProductSummary product(Product product) {
		return new ProductSummary(product.getId(), product.getSku(), product.getName(), product.getCategory(),
				product.getKind(), product.isActive());
	}

}
