package dev.jeffrojas.electronicarojas.dashboard;

import java.time.Instant;
import java.util.List;

import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus;

/** HTTP contract of the dashboard: counts by stable key; the SPA maps each key to a label and a list. */
public final class DashboardDtos {

	private DashboardDtos() {
	}

	/**
	 * @param branch the branch shown, or null for all the user's branches ({@code consolidated})
	 * @param indicators counts, each equal to the total of the list its key names
	 */
	public record DashboardResponse(BranchSummary branch, boolean consolidated, List<Indicator> indicators,
			List<UpcomingVisit> upcomingVisits, Instant generatedAt) {
	}

	public record Indicator(String key, long count) {
	}

	/** A visit of the next days: time, what, where (canton only) and who. */
	public record UpcomingVisit(long id, String requestCode, VisitStatus status, Instant start, Instant end,
			String technicianName, String deviceType, String canton, BranchSummary branch) {
	}

}
