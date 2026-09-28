package dev.jeffrojas.electronicarojas.servicerequests;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.jeffrojas.electronicarojas.notifications.NotificationEnums.SubjectType;
import dev.jeffrojas.electronicarojas.notifications.NotificationOutbox;
import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;
import dev.jeffrojas.electronicarojas.customers.Customer;
import dev.jeffrojas.electronicarojas.repairs.RepairOrder;
import dev.jeffrojas.electronicarojas.security.CurrentUser;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.EventView;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PersonRef;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.RepairOrderRef;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.RequestActions;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ServiceRequestDetail;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.ServiceRequestSummary;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.VisitActions;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.VisitBrief;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.VisitView;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitOutcome;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus;
import dev.jeffrojas.electronicarojas.users.AppUser;

/** Builds the DTOs of the module, applying data minimization and computing allowed actions. */
@Component
class ServiceViews {

	private final ServiceVisitRepository visits;

	private final ServiceRequestEventRepository events;

	private final NotificationOutbox notifications;

	ServiceViews(ServiceVisitRepository visits, ServiceRequestEventRepository events, NotificationOutbox notifications) {
		this.notifications = notifications;
		this.visits = visits;
		this.events = events;
	}

	static PersonRef person(AppUser user) {
		return user == null ? null : new PersonRef(user.getId(), user.getFullName());
	}

	static PersonRef person(Customer customer) {
		return customer == null ? null : new PersonRef(customer.getId(), customer.getFullName());
	}

	static VisitBrief brief(ServiceVisit visit) {
		return visit == null ? null
				: new VisitBrief(visit.getId(), visit.getStatus(), visit.getScheduledStart(), visit.getScheduledEnd(),
						person(visit.getTechnician()));
	}

	static ServiceRequestSummary summary(ServiceRequest request, ServiceVisit activeVisit) {
		return new ServiceRequestSummary(request.getId(), request.getRequestCode(), request.getStatus(),
				request.getChannel(), BranchSummary.from(request.getBranch()), request.getContactName(),
				person(request.getCustomer()), request.getDeviceType(), request.getProvince(), request.getCanton(),
				request.getPreferredDate(), request.getPreferredWindow(), request.getCreatedAt(), brief(activeVisit));
	}

	ServiceRequestDetail detail(CurrentUser user, ServiceRequest request) {
		List<ServiceVisit> requestVisits = visits.findForRequest(request.getId());
		boolean hasActiveVisit = requestVisits.stream().anyMatch(ServiceVisit::isActive);
		boolean completed = requestVisits.stream().anyMatch(v -> v.getStatus() == VisitStatus.COMPLETED);
		boolean running = requestVisits.stream().anyMatch(v -> v.getStatus() == VisitStatus.IN_PROGRESS);
		RequestStatus status = request.getStatus();
		boolean staff = ServicePolicy.isStaff(user);
		boolean open = ServicePolicy.isOpen(status);
		RequestActions actions = new RequestActions(staff && status == RequestStatus.PENDING,
				staff && open && request.getCustomer() == null,
				ServicePolicy.mayChangeBranch(user) && open && !hasActiveVisit,
				staff && open && !hasActiveVisit && !completed && request.getCustomer() != null,
				staff && (status == RequestStatus.PENDING || status == RequestStatus.UNDER_REVIEW),
				staff && open && !running && !completed);
		return new ServiceRequestDetail(request.getId(), request.getRequestCode(), status, request.getChannel(),
				BranchSummary.from(request.getBranch()), person(request.getCustomer()), request.getContactName(),
				request.getContactPhone(), request.getContactEmail(), request.getProvince(), request.getCanton(),
				request.getDistrict(), request.getAddressLine(), request.getDeviceType(), request.getBrand(),
				request.getModel(), request.getProblemDescription(), request.getPreferredDate(),
				request.getPreferredWindow(), request.getAdditionalNotes(), request.isNotificationsConsent(),
				request.isEmailConsent(), request.isWhatsappConsent(), request.getContactConsentAt(), request.getDecisionReason(), person(request.getCreatedBy()),
				request.getCreatedAt(), request.getVersion(),
				requestVisits.stream().map(visit -> visit(user, visit)).toList(),
				events.findTimeline(request.getId())
					.stream()
					.map(event -> new EventView(event.getEventType(), event.getFromStatus(), event.getToStatus(),
							event.getDetails(), event.getReason(), person(event.getActor()), event.getOccurredAt(),
							event.getVisit() == null ? null : event.getVisit().getId()))
					.toList(),
				notifications.statusFor(SubjectType.SERVICE_VISIT, requestVisits.stream().map(ServiceVisit::getId).toList()),
				actions);
	}

	VisitView visit(CurrentUser user, ServiceVisit visit) {
		ServiceRequest request = visit.getRequest();
		boolean contact = ServicePolicy.mayViewContact(user, visit);
		boolean staff = ServicePolicy.isStaff(user);
		boolean worker = ServicePolicy.mayWorkVisit(user, visit);
		VisitStatus status = visit.getStatus();
		VisitActions actions = new VisitActions(staff && status == VisitStatus.PROPOSED,
				staff && ServicePolicy.canReschedule(status), staff && ServicePolicy.canReschedule(status),
				worker && status == VisitStatus.CONFIRMED, worker && status == VisitStatus.IN_PROGRESS,
				staff && status == VisitStatus.COMPLETED && visit.getOutcome() == VisitOutcome.NEEDS_WORKSHOP
						&& visit.getRepairOrder() == null);
		RepairOrder order = visit.getRepairOrder();
		return new VisitView(visit.getId(), request.getId(), request.getRequestCode(), status,
				BranchSummary.from(request.getBranch()), person(visit.getTechnician()), visit.getScheduledStart(),
				visit.getScheduledEnd(), visit.getBlockedUntil(), person(request.getCustomer()),
				request.getContactName(), contact ? request.getContactPhone() : null, request.getProvince(),
				request.getCanton(), request.getDistrict(), contact ? request.getAddressLine() : null,
				request.getDeviceType(), request.getBrand(), request.getModel(), request.getProblemDescription(),
				visit.getStartedAt(), visit.getCompletedAt(), visit.getOutcome(), visit.getOutcomeNotes(),
				visit.getCancelReason(), order == null ? null : new RepairOrderRef(order.getId(), order.getOrderCode()),
				visit.getVersion(), actions);
	}

}
