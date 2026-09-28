package dev.jeffrojas.electronicarojas.servicerequests;

/**
 * Repositories of the home-service module live in their own files; this note documents the locks
 * they take (see ADR-013):
 * <ul>
 * <li>{@code ServiceRequestRepository.lockById}: row lock on a request for every decision.</li>
 * <li>{@code ServiceVisitRepository.lockTechnicianSchedule}: transaction-scoped advisory lock
 * (namespace 2, technician id) held while checking and writing a technician's confirmed time, so
 * two confirmations of the same technician are serialized and the loser sees the winner's
 * visit. The V9 exclusion constraint is the last line of defence.</li>
 * <li>{@code ServiceRequestRepository.lockSubmission}: advisory lock (namespace 1) on a public
 * submission id, so concurrent duplicates of one submit wait and replay the first.</li>
 * </ul>
 */
final class ServiceRepositories {

	static final int SUBMISSION_LOCK_NAMESPACE = 1;

	static final int TECHNICIAN_LOCK_NAMESPACE = 2;

	private ServiceRepositories() {
	}

}
