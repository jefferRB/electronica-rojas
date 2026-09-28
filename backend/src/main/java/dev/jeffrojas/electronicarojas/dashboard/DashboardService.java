package dev.jeffrojas.electronicarojas.dashboard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.dashboard.DashboardDtos.DashboardResponse;
import dev.jeffrojas.electronicarojas.dashboard.DashboardDtos.Indicator;
import dev.jeffrojas.electronicarojas.dashboard.DashboardDtos.UpcomingVisit;
import dev.jeffrojas.electronicarojas.inventory.BranchStockService;
import dev.jeffrojas.electronicarojas.inventory.StockStatusFilter;
import dev.jeffrojas.electronicarojas.repairs.RepairOrderService;
import dev.jeffrojas.electronicarojas.repairs.RepairOrderService.OrderFilter;
import dev.jeffrojas.electronicarojas.repairs.RepairStatus;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.security.Role;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceRequestService;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceRequestService.RequestFilter;
import dev.jeffrojas.electronicarojas.servicerequests.VisitService;
import dev.jeffrojas.electronicarojas.shared.ClockConfig;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;

/**
 * Operational dashboard (D.1/D.2). Every number comes from the SAME use case, filters and branch
 * scope as the list the card links to, called with a page of one row: the count is SQL
 * {@code COUNT(*)} of that list, so the card and the list can never disagree, and no record is
 * downloaded to be counted in the browser. Authorization is the lists' own (403/404 unchanged).
 * <ul>
 * <li>ADMIN and BRANCH_MANAGER may ask for one branch or for all their branches together.</li>
 * <li>RECEPTIONIST works on one selected branch at a time.</li>
 * <li>TECHNICIAN gets their own work only: assigned repairs by stage and their visits.</li>
 * </ul>
 */
@Service
public class DashboardService {

	/** How far ahead the "upcoming visits" list looks, and how many it shows. */
	static final Duration UPCOMING_WINDOW = Duration.ofDays(7);

	static final int UPCOMING_LIMIT = 6;

	private final BranchService branchService;

	private final RepairOrderService repairs;

	private final ServiceRequestService requests;

	private final VisitService visits;

	private final BranchStockService stock;

	private final Clock clock;

	DashboardService(BranchService branchService, RepairOrderService repairs, ServiceRequestService requests,
			VisitService visits, BranchStockService stock, Clock clock) {
		this.branchService = branchService;
		this.repairs = repairs;
		this.requests = requests;
		this.visits = visits;
		this.stock = stock;
		this.clock = clock;
	}

	@PreAuthorize("isAuthenticated()")
	public DashboardResponse dashboard(CurrentUser user, Long branchId) {
		BranchSummary scope = null;
		if (branchId != null) {
			scope = BranchSummary.from(branchService.requireReadable(user, branchId));
		}
		else if (user.role() == Role.RECEPTIONIST) {
			throw new InvalidRequestException("branchId", "BRANCH_REQUIRED", "choose the branch to show");
		}
		boolean technician = user.role() == Role.TECHNICIAN;
		List<Indicator> indicators = new ArrayList<>();
		if (technician) {
			// The list use case already limits a technician to their assigned orders.
			for (RepairStatus status : List.of(RepairStatus.RECEIVED, RepairStatus.DIAGNOSING, RepairStatus.APPROVED,
					RepairStatus.IN_REPAIR)) {
				indicators.add(new Indicator("MY_REPAIRS_" + status, repairCount(user, branchId, status)));
			}
		}
		else {
			indicators.add(new Indicator("REPAIRS_RECEIVED", repairCount(user, branchId, RepairStatus.RECEIVED)));
			indicators.add(new Indicator("REPAIRS_DIAGNOSING", repairCount(user, branchId, RepairStatus.DIAGNOSING)));
			indicators.add(new Indicator("REPAIRS_READY", repairCount(user, branchId, RepairStatus.READY_FOR_PICKUP)));
			indicators.add(new Indicator("REQUESTS_PENDING", requestCount(user, branchId, RequestStatus.PENDING)));
			indicators.add(new Indicator("REQUESTS_UNDER_REVIEW", requestCount(user, branchId, RequestStatus.UNDER_REVIEW)));
		}

		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, ClockConfig.BUSINESS_ZONE);
		Instant dayStart = today.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
		Instant dayEnd = today.plusDays(1).atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
		// One day of the agenda (bounded; the same query as the agenda screen, cancelled excluded).
		indicators.add(new Indicator(technician ? "MY_VISITS_TODAY" : "VISITS_TODAY",
				visits.agenda(user, dayStart, dayEnd, branchId, null).size()));
		List<UpcomingVisit> upcoming = visits.agenda(user, now, now.plus(UPCOMING_WINDOW), branchId, null)
			.stream()
			.filter(visit -> visit.status() == VisitStatus.CONFIRMED || visit.status() == VisitStatus.PROPOSED
					|| visit.status() == VisitStatus.IN_PROGRESS)
			.limit(UPCOMING_LIMIT)
			.map(visit -> new UpcomingVisit(visit.id(), visit.requestCode(), visit.status(), visit.start(), visit.end(),
					visit.technician().fullName(), visit.deviceType(), visit.canton(), visit.branch()))
			.toList();

		if (!technician) {
			indicators.add(new Indicator("STOCK_OUT", stockCount(user, branchId, StockStatusFilter.OUT_OF_STOCK)));
			indicators.add(new Indicator("STOCK_LOW", stockCount(user, branchId, StockStatusFilter.LOW)));
		}
		return new DashboardResponse(scope, branchId == null, indicators, upcoming, now);
	}

	private long repairCount(CurrentUser user, Long branchId, RepairStatus status) {
		return repairs.list(user, new OrderFilter(branchId, status, null, null, null, null, null), 0, 1).totalElements();
	}

	private long requestCount(CurrentUser user, Long branchId, RequestStatus status) {
		return requests.list(user, new RequestFilter(branchId, status, null, null), 0, 1).totalElements();
	}

	/**
	 * Active products out of stock or below the minimum: the branch stock list with its default
	 * filter (active products), or the consolidated view across the user's branches.
	 */
	private long stockCount(CurrentUser user, Long branchId, StockStatusFilter filter) {
		return branchId != null ? stock.list(user, branchId, null, null, null, true, filter, 0, 1).totalElements()
				: stock.overview(user, null, null, null, true, filter, 0, 1).rows().totalElements();
	}

}
