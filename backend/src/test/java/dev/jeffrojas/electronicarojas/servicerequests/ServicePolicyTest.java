package dev.jeffrojas.electronicarojas.servicerequests;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.security.Role;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus;

/** BR-SRV-002/003: the request and visit transition tables and role rules. */
class ServicePolicyTest {

	private static CurrentUser user(Role role) {
		return new CurrentUser(1, "u@electronica-rojas.test", "Usuario", role, true, 0, null);
	}

	@Test
	void requestTableIsExactlyTheDocumentedOne() {
		assertRequest(RequestStatus.PENDING, RequestStatus.UNDER_REVIEW, RequestStatus.REJECTED, RequestStatus.CANCELLED);
		assertRequest(RequestStatus.UNDER_REVIEW, RequestStatus.ACCEPTED, RequestStatus.REJECTED, RequestStatus.CANCELLED);
		assertRequest(RequestStatus.ACCEPTED, RequestStatus.UNDER_REVIEW, RequestStatus.CANCELLED);
		assertRequest(RequestStatus.REJECTED);
		assertRequest(RequestStatus.CANCELLED);
	}

	@Test
	void visitTableIsExactlyTheDocumentedOne() {
		assertVisit(VisitStatus.PROPOSED, VisitStatus.CONFIRMED, VisitStatus.CANCELLED);
		assertVisit(VisitStatus.CONFIRMED, VisitStatus.IN_PROGRESS, VisitStatus.CANCELLED);
		assertVisit(VisitStatus.IN_PROGRESS, VisitStatus.COMPLETED);
		assertVisit(VisitStatus.COMPLETED);
		assertVisit(VisitStatus.CANCELLED);
		assertThat(ServicePolicy.canReschedule(VisitStatus.PROPOSED)).isTrue();
		assertThat(ServicePolicy.canReschedule(VisitStatus.CONFIRMED)).isTrue();
		assertThat(ServicePolicy.canReschedule(VisitStatus.IN_PROGRESS)).isFalse();
	}

	@Test
	void rolesPerAction() {
		assertThat(ServicePolicy.isStaff(user(Role.RECEPTIONIST))).isTrue();
		assertThat(ServicePolicy.isStaff(user(Role.TECHNICIAN))).isFalse();
		assertThat(ServicePolicy.mayChangeBranch(user(Role.BRANCH_MANAGER))).isTrue();
		assertThat(ServicePolicy.mayChangeBranch(user(Role.RECEPTIONIST))).isFalse();
	}

	private static void assertRequest(RequestStatus from, RequestStatus... to) {
		Set<RequestStatus> allowed = to.length == 0 ? EnumSet.noneOf(RequestStatus.class) : EnumSet.of(to[0], to);
		for (RequestStatus target : RequestStatus.values()) {
			assertThat(ServicePolicy.canMove(from, target)).as(from + " -> " + target).isEqualTo(allowed.contains(target));
		}
	}

	private static void assertVisit(VisitStatus from, VisitStatus... to) {
		Set<VisitStatus> allowed = to.length == 0 ? EnumSet.noneOf(VisitStatus.class) : EnumSet.of(to[0], to);
		for (VisitStatus target : VisitStatus.values()) {
			assertThat(ServicePolicy.canMove(from, target)).as(from + " -> " + target).isEqualTo(allowed.contains(target));
		}
	}

}
