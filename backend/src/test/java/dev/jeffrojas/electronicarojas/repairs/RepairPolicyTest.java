package dev.jeffrojas.electronicarojas.repairs;

import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.APPROVED;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.AWAITING_APPROVAL;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.CANCELLED;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.DELIVERED;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.DIAGNOSING;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.IN_REPAIR;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.READY_FOR_PICKUP;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.RECEIVED;
import static dev.jeffrojas.electronicarojas.repairs.RepairStatus.UNREPAIRABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.jeffrojas.electronicarojas.repairs.RepairEnums.Resolution;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.security.Role;
import dev.jeffrojas.electronicarojas.users.AppUser;

/** BR-REP-002/003: the transition table, quote-only moves and who may request what. */
class RepairPolicyTest {

	private static final long TECHNICIAN_ID = 40;

	private static final CurrentUser ADMIN = user(1, Role.ADMIN);

	private static final CurrentUser MANAGER = user(2, Role.BRANCH_MANAGER);

	private static final CurrentUser RECEPTIONIST = user(3, Role.RECEPTIONIST);

	private static final CurrentUser ASSIGNED_TECHNICIAN = user(TECHNICIAN_ID, Role.TECHNICIAN);

	private static final CurrentUser OTHER_TECHNICIAN = user(41, Role.TECHNICIAN);

	private static CurrentUser user(long id, Role role) {
		return new CurrentUser(id, role + "@electronica-rojas.test", "Usuario", role, true, 0, null);
	}

	/** An order in {@code status}, assigned to TECHNICIAN_ID when {@code withTechnician}. */
	private static RepairOrder order(RepairStatus status, boolean withTechnician) {
		RepairOrder order = new RepairOrder("OR-2026-000001", UUID.randomUUID(), "fp", null, null,
				new RepairOrder.Intake("Televisor", "Marca", null, null, "No enciende", "Rayones", null), null,
				Instant.EPOCH);
		if (withTechnician) {
			AppUser technician = mock(AppUser.class);
			when(technician.getId()).thenReturn(TECHNICIAN_ID);
			order.assignTechnician(technician, Instant.EPOCH);
		}
		if (status != RECEIVED) {
			order.moveTo(status, RepairPolicy.resolutionFor(status), null, Instant.EPOCH);
		}
		return order;
	}

	@Test
	void transitionTableIsExactlyTheDocumentedOne() {
		assertTargets(RECEIVED, DIAGNOSING, CANCELLED);
		assertTargets(DIAGNOSING, AWAITING_APPROVAL, IN_REPAIR, UNREPAIRABLE, CANCELLED);
		assertTargets(AWAITING_APPROVAL, APPROVED, CANCELLED);
		assertTargets(APPROVED, IN_REPAIR, CANCELLED);
		assertTargets(IN_REPAIR, READY_FOR_PICKUP, AWAITING_APPROVAL, UNREPAIRABLE);
		assertTargets(READY_FOR_PICKUP, DELIVERED);
		assertTargets(CANCELLED, DELIVERED);
		assertTargets(UNREPAIRABLE, DELIVERED);
		assertTargets(DELIVERED);
	}

	private static void assertTargets(RepairStatus from, RepairStatus... expected) {
		Set<RepairStatus> allowed = expected.length == 0 ? EnumSet.noneOf(RepairStatus.class) : EnumSet.of(expected[0], expected);
		for (RepairStatus to : RepairStatus.values()) {
			assertThat(RepairPolicy.exists(from, to)).as(from + " -> " + to).isEqualTo(allowed.contains(to));
		}
	}

	@Test
	void deliveredIsFinalForEveryone() {
		RepairOrder delivered = order(DELIVERED, true);
		for (CurrentUser user : new CurrentUser[] { ADMIN, MANAGER, RECEPTIONIST, ASSIGNED_TECHNICIAN }) {
			assertThat(RepairPolicy.availableTransitions(user, delivered)).isEmpty();
			assertThat(RepairPolicy.mayEditDiagnosis(user, delivered)).isFalse();
			assertThat(RepairPolicy.mayAssignTechnician(user, delivered)).isFalse();
		}
	}

	@Test
	void quoteDrivenTransitionsAreNeverOfferedManually() {
		assertThat(RepairPolicy.isQuoteDriven(DIAGNOSING, AWAITING_APPROVAL)).isTrue();
		assertThat(RepairPolicy.isQuoteDriven(AWAITING_APPROVAL, APPROVED)).isTrue();
		assertThat(RepairPolicy.isQuoteDriven(AWAITING_APPROVAL, CANCELLED)).isTrue();
		assertThat(RepairPolicy.isQuoteDriven(APPROVED, CANCELLED)).isFalse();
		assertThat(RepairPolicy.availableTransitions(ADMIN, order(AWAITING_APPROVAL, true))).isEmpty();
		assertThat(RepairPolicy.availableTransitions(ADMIN, order(DIAGNOSING, true)))
			.containsExactlyInAnyOrder(IN_REPAIR, UNREPAIRABLE, CANCELLED);
	}

	@Test
	void technicalWorkBelongsToManagementAndTheAssignedTechnician() {
		RepairOrder diagnosing = order(DIAGNOSING, true);
		assertThat(RepairPolicy.mayRequest(ASSIGNED_TECHNICIAN, diagnosing, IN_REPAIR)).isTrue();
		assertThat(RepairPolicy.mayRequest(MANAGER, diagnosing, IN_REPAIR)).isTrue();
		assertThat(RepairPolicy.mayRequest(OTHER_TECHNICIAN, diagnosing, IN_REPAIR)).isFalse();
		assertThat(RepairPolicy.mayRequest(RECEPTIONIST, diagnosing, IN_REPAIR)).isFalse();
		// The technician may not cancel (a counter act); the receptionist may.
		assertThat(RepairPolicy.mayRequest(ASSIGNED_TECHNICIAN, diagnosing, CANCELLED)).isFalse();
		assertThat(RepairPolicy.mayRequest(RECEPTIONIST, diagnosing, CANCELLED)).isTrue();
	}

	@Test
	void deliveryIsACounterAct() {
		RepairOrder ready = order(READY_FOR_PICKUP, true);
		assertThat(RepairPolicy.mayRequest(RECEPTIONIST, ready, DELIVERED)).isTrue();
		assertThat(RepairPolicy.mayRequest(ASSIGNED_TECHNICIAN, ready, DELIVERED)).isFalse();
		assertThat(RepairPolicy.availableTransitions(RECEPTIONIST, order(UNREPAIRABLE, true))).containsExactly(DELIVERED);
	}

	@ParameterizedTest
	@EnumSource(value = RepairStatus.class, names = { "CANCELLED", "UNREPAIRABLE" })
	void closingWithoutRepairNeedsAReason(RepairStatus target) {
		assertThat(RepairPolicy.requiresReason(target)).isTrue();
	}

	@Test
	void resolutionFollowsHowTheWorkEnded() {
		assertThat(RepairPolicy.resolutionFor(READY_FOR_PICKUP)).isEqualTo(Resolution.REPAIRED);
		assertThat(RepairPolicy.resolutionFor(CANCELLED)).isEqualTo(Resolution.CANCELLED);
		assertThat(RepairPolicy.resolutionFor(UNREPAIRABLE)).isEqualTo(Resolution.UNREPAIRABLE);
		assertThat(RepairPolicy.resolutionFor(DELIVERED)).isNull();
		assertThat(RepairPolicy.requiresTechnician(DIAGNOSING)).isTrue();
		assertThat(RepairPolicy.requiresTechnician(IN_REPAIR)).isTrue();
	}

	@Test
	void quotesAreIssuedByTheWorkshopAndDecidedAtTheCounter() {
		assertThat(RepairPolicy.mayCreateQuote(ASSIGNED_TECHNICIAN, order(DIAGNOSING, true))).isTrue();
		assertThat(RepairPolicy.mayCreateQuote(ASSIGNED_TECHNICIAN, order(IN_REPAIR, true))).isTrue();
		assertThat(RepairPolicy.mayCreateQuote(ASSIGNED_TECHNICIAN, order(RECEIVED, true))).isFalse();
		assertThat(RepairPolicy.mayCreateQuote(RECEPTIONIST, order(DIAGNOSING, true))).isFalse();
		assertThat(RepairPolicy.mayDecideQuote(RECEPTIONIST, order(AWAITING_APPROVAL, true))).isTrue();
		assertThat(RepairPolicy.mayDecideQuote(ASSIGNED_TECHNICIAN, order(AWAITING_APPROVAL, true))).isFalse();
		assertThat(RepairPolicy.mayDecideQuote(MANAGER, order(APPROVED, true))).isFalse();
	}

	@Test
	void techniciansNeverSeeCustomerContactData() {
		assertThat(RepairPolicy.mayViewCustomerContact(ASSIGNED_TECHNICIAN)).isFalse();
		assertThat(RepairPolicy.mayViewCustomerContact(RECEPTIONIST)).isTrue();
	}

	// ---- spare parts (BR-REP-011/012) ----

	@Test
	void partsAreConsumedOnlyInRepairByTheWorkshop() {
		for (RepairStatus status : RepairStatus.values()) {
			RepairOrder order = order(status, true);
			boolean inRepair = status == IN_REPAIR;
			assertThat(RepairPolicy.mayConsumeParts(ADMIN, order)).as("admin in %s", status).isEqualTo(inRepair);
			assertThat(RepairPolicy.mayConsumeParts(MANAGER, order)).as("manager in %s", status).isEqualTo(inRepair);
			assertThat(RepairPolicy.mayConsumeParts(ASSIGNED_TECHNICIAN, order)).as("technician in %s", status)
				.isEqualTo(inRepair);
			assertThat(RepairPolicy.mayConsumeParts(OTHER_TECHNICIAN, order)).isFalse();
			assertThat(RepairPolicy.mayConsumeParts(RECEPTIONIST, order)).isFalse();
		}
	}

	@Test
	void closedOrdersAreCorrectedOnlyByManagement() {
		for (RepairStatus status : RepairStatus.values()) {
			RepairOrder order = order(status, true);
			boolean open = !RepairPolicy.isClosed(order);
			assertThat(RepairPolicy.mayReturnParts(ADMIN, order)).isTrue();
			assertThat(RepairPolicy.mayReturnParts(MANAGER, order)).isTrue();
			assertThat(RepairPolicy.mayReturnParts(ASSIGNED_TECHNICIAN, order)).as("technician in %s", status)
				.isEqualTo(open);
			assertThat(RepairPolicy.mayReturnParts(OTHER_TECHNICIAN, order)).isFalse();
			assertThat(RepairPolicy.mayReturnParts(RECEPTIONIST, order)).isFalse();
		}
		assertThat(RepairPolicy.isClosed(order(READY_FOR_PICKUP, true))).isTrue();
		assertThat(RepairPolicy.isClosed(order(DELIVERED, true))).isTrue();
		assertThat(RepairPolicy.isClosed(order(IN_REPAIR, true))).isFalse();
	}

}
