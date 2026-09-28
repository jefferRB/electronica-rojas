package dev.jeffrojas.electronicarojas.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Every summary format written by Phase 2 is read back exactly; unknown text yields nothing. */
class LegacyAuditSummariesTest {

	@Test
	void transfersKeepQuantitySkuBranchesDirectionAndBalance() {
		assertThat(LegacyAuditSummaries.parse("STOCK_TRANSFER_COMPLETED",
				"Transfer of 4 x CMP-100 from SJ-01 to AL-01 (sent, balance 6)"))
			.containsExactlyInAnyOrderEntriesOf(Map.of("quantity", 4, "sku", "CMP-100", "sourceBranchCode", "SJ-01",
					"destinationBranchCode", "AL-01", "direction", "SENT", "balanceAfter", 6));
	}

	@Test
	void movementsUseTheAbsoluteQuantity() {
		assertThat(LegacyAuditSummaries.parse("STOCK_MOVEMENT_RECORDED", "ISSUE -1 x CMP-100 at AL-01 (balance 4 -> 3)"))
			.containsEntry("movementType", "ISSUE")
			.containsEntry("quantity", 1)
			.containsEntry("balanceBefore", 4)
			.containsEntry("balanceAfter", 3);
		assertThat(LegacyAuditSummaries.parse("STOCK_MOVEMENT_RECORDED", "RECEIPT 10 x CMP-100 at SJ-01 (balance 0 -> 10)"))
			.containsEntry("quantity", 10);
	}

	@Test
	void catalogBranchUserAndMinimumFormats() {
		assertThat(LegacyAuditSummaries.parse("PRODUCT_CREATED", "Product LAV-200 created (MERCHANDISE)"))
			.containsEntry("sku", "LAV-200")
			.containsEntry("kind", "MERCHANDISE");
		assertThat(LegacyAuditSummaries.parse("BRANCH_UPDATED", "Branch HE-01 updated (active=false)"))
			.containsEntry("active", false);
		assertThat(LegacyAuditSummaries.parse("USER_UPDATED", "Account updated: role TECHNICIAN, active=true, 2 branch(es)"))
			.containsEntry("role", "TECHNICIAN")
			.containsEntry("branchCount", 2);
		assertThat(LegacyAuditSummaries.parse("USER_CREATED", "Initial administrator created by bootstrap"))
			.containsEntry("bootstrap", true);
		assertThat(LegacyAuditSummaries.parse("STOCK_MINIMUM_CHANGED", "Minimum of CMP-100 at SJ-01 changed from 0 to 5"))
			.containsEntry("previousMinimum", 0)
			.containsEntry("newMinimum", 5);
	}

	@Test
	void unknownTextOrMismatchedActionIsNotGuessed() {
		assertThat(LegacyAuditSummaries.parse("STOCK_TRANSFER_COMPLETED", "something else")).isEmpty();
		assertThat(LegacyAuditSummaries.parse("PRODUCT_CREATED", "Branch SJ-01 created")).isEmpty();
	}

}
