package dev.jeffrojas.electronicarojas.servicerequests;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jeffrojas.electronicarojas.branches.Branch;
import dev.jeffrojas.electronicarojas.branches.BranchService;
import dev.jeffrojas.electronicarojas.customers.NotificationConsentText;
import dev.jeffrojas.electronicarojas.customers.PhoneNumbers;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PublicBranch;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PublicReceipt;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PublicRequestStatus;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PublicStatus;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceDtos.PublicSubmission;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.EventType;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestChannel;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.RequestStatus;
import dev.jeffrojas.electronicarojas.servicerequests.ServiceEnums.VisitStatus;
import dev.jeffrojas.electronicarojas.shared.OperationFingerprint;
import dev.jeffrojas.electronicarojas.shared.web.ConflictException;
import dev.jeffrojas.electronicarojas.shared.web.InvalidRequestException;
import dev.jeffrojas.electronicarojas.shared.web.NotFoundException;
import dev.jeffrojas.electronicarojas.shared.web.RateLimitedException;

/**
 * The anonymous side of home service (FR-SRV-001/002, BR-SRV-001/004). An anonymous caller can
 * only create a request and read the coarse status of one request by its unguessable reference:
 * nothing here searches customers or tells whether a phone or e-mail is already known. What the
 * form accepts (on/off, dates, windows, provinces) comes from the portal settings (BR-SRV-009).
 */
@Service
public class PublicServiceRequestService {

	private final ServiceRequestRepository requests;

	private final ServiceVisitRepository visits;

	private final BranchService branchService;

	private final ServiceTimeline timeline;

	private final PublicRequestLimiter limiter;

	private final PortalSettingsService portal;

	private final Clock clock;

	PublicServiceRequestService(ServiceRequestRepository requests, ServiceVisitRepository visits,
			BranchService branchService, ServiceTimeline timeline, PublicRequestLimiter limiter,
			PortalSettingsService portal, Clock clock) {
		this.requests = requests;
		this.visits = visits;
		this.branchService = branchService;
		this.timeline = timeline;
		this.limiter = limiter;
		this.portal = portal;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<PublicBranch> branches() {
		return branchService.activeBranches().stream().map(branch -> new PublicBranch(branch.getId(), branch.getName())).toList();
	}

	@Transactional
	public PublicReceipt submit(PublicSubmission form, String clientAddress) {
		if (form.website() != null && !form.website().isBlank()) {
			// Honeypot filled: a bot. Answer like a success, store nothing, reveal nothing.
			return new PublicReceipt(UUID.randomUUID(), null, PublicStatus.RECEIVED);
		}
		String phone = PhoneNumbers.normalize(form.contactPhone())
			.orElseThrow(() -> new InvalidRequestException("contactPhone", "PHONE_INVALID", "is not a valid phone number"));
		boolean email = Boolean.TRUE.equals(form.emailNotifications());
		boolean whatsapp = Boolean.TRUE.equals(form.whatsappNotifications());
		if (email && (form.contactEmail() == null || form.contactEmail().isBlank())) {
			throw new InvalidRequestException("contactEmail", "EMAIL_REQUIRED_FOR_CONSENT",
					"an e-mail address is needed to receive e-mail notices");
		}
		if ((email || whatsapp) && !NotificationConsentText.CURRENT_VERSION.equals(form.consentTextVersion())) {
			throw new InvalidRequestException("consentTextVersion", "CONSENT_TEXT_OUTDATED",
					"the accepted text must be version " + NotificationConsentText.CURRENT_VERSION);
		}
		String fingerprint = OperationFingerprint.of("SERVICE_REQUEST", form.branchId(), form.contactName(), phone,
				form.contactEmail(), form.province(), form.canton(), form.district(), form.addressLine(),
				form.deviceType(), form.brand(), form.model(), form.problemDescription(), form.preferredDate(),
				form.preferredWindow(), form.additionalNotes(), form.emailNotifications(), form.whatsappNotifications(),
				form.consentTextVersion());

		// Concurrent duplicates of one submit wait for the first and replay it.
		requests.lockSubmission(form.submissionId().hashCode());
		Optional<ServiceRequest> replay = requests.findBySubmissionId(form.submissionId());
		if (replay.isPresent()) {
			if (!replay.get().getSubmissionFingerprint().equals(fingerprint)) {
				throw new ConflictException("OPERATION_ID_REUSED",
						"This submission id was already used for a different request.");
			}
			return receipt(replay.get());
		}

		// A replay above still returns its receipt; a new request needs the portal open and its rules.
		PortalRules.check(portal.acceptingRules(), portal.today(), form.preferredDate(), form.preferredWindow(),
				form.province());
		limiter.blockedFor(clientAddress, phone).ifPresent(wait -> {
			throw new RateLimitedException(wait);
		});
		Branch branch = branchService.activeBranches()
			.stream()
			.filter(candidate -> candidate.getId().equals(form.branchId()))
			.findFirst()
			.orElseThrow(() -> new InvalidRequestException("branchId", "BRANCH_INVALID", "choose one of the listed branches"));

		Instant now = clock.instant();
		ServiceRequest request = requests.saveAndFlush(new ServiceRequest(nextCode(now), form.submissionId(), fingerprint,
				RequestChannel.PUBLIC_FORM, branch,
				new ServiceRequest.Submission(form.contactName(), phone, form.contactEmail(), form.province(),
						form.canton(), form.district(), form.addressLine(), form.deviceType(), form.brand(), form.model(),
						form.problemDescription(), form.preferredDate(), form.preferredWindow(), form.additionalNotes(),
						email || whatsapp, email, whatsapp, form.consentTextVersion()),
				null, now));
		timeline.record(null, request, null, EventType.SUBMITTED, null, RequestStatus.PENDING, null, null);
		limiter.recordAccepted(clientAddress, phone);
		return receipt(request);
	}

	/** Coarse status for the customer; 404 for an unknown reference (UUIDs cannot be enumerated). */
	@Transactional(readOnly = true)
	public PublicRequestStatus status(UUID publicRef) {
		ServiceRequest request = requests.findByPublicRef(publicRef)
			.orElseThrow(() -> new NotFoundException("Service request not found."));
		ServiceVisit visit = visits.findForRequest(request.getId())
			.stream()
			.filter(candidate -> candidate.getStatus() != VisitStatus.CANCELLED)
			.reduce((first, second) -> second)
			.orElse(null);
		PublicStatus status = publicStatus(request.getStatus(), visit);
		boolean showTime = visit != null && visit.getStatus() != VisitStatus.PROPOSED;
		ZonedDateTime from = showTime ? visit.getScheduledStart().atZone(ScheduleRules.ZONE) : null;
		ZonedDateTime to = showTime ? visit.getScheduledEnd().atZone(ScheduleRules.ZONE) : null;
		return new PublicRequestStatus(request.getRequestCode(), status, request.getBranch().getName(),
				request.getDeviceType(), request.getPreferredDate(), request.getPreferredWindow(),
				from == null ? null : from.toLocalDate(), from == null ? null : from.toLocalTime(),
				to == null ? null : to.toLocalTime(), request.getCreatedAt());
	}

	static PublicStatus publicStatus(RequestStatus status, ServiceVisit visit) {
		return switch (status) {
			case PENDING -> PublicStatus.RECEIVED;
			case UNDER_REVIEW -> PublicStatus.IN_REVIEW;
			case REJECTED -> PublicStatus.NOT_ACCEPTED;
			case CANCELLED -> PublicStatus.CANCELLED;
			case ACCEPTED -> visit == null ? PublicStatus.SCHEDULED : switch (visit.getStatus()) {
				case IN_PROGRESS -> PublicStatus.IN_PROGRESS;
				case COMPLETED -> PublicStatus.COMPLETED;
				default -> PublicStatus.SCHEDULED;
			};
		};
	}

	private PublicReceipt receipt(ServiceRequest request) {
		return new PublicReceipt(request.getPublicRef(), request.getRequestCode(), PublicStatus.RECEIVED);
	}

	/** SR-2026-000123: year in Costa Rica and a global sequence (same scheme as repair orders). */
	String nextCode(Instant now) {
		return "SR-" + now.atZone(ScheduleRules.ZONE).getYear() + "-"
				+ String.format(Locale.ROOT, "%06d", requests.nextRequestNumber());
	}

}
