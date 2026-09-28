package dev.jeffrojas.electronicarojas.customers;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import dev.jeffrojas.electronicarojas.branches.BranchDtos.BranchSummary;

/** HTTP contracts of the customers module (ENG-031). */
public final class CustomerDtos {

	private CustomerDtos() {
	}

	public record CustomerResponse(long id, String fullName, String phone, String email, String address,
			String internalNotes, BranchSummary registeredBranch, Instant createdAt, Instant updatedAt, long version) {

		static CustomerResponse from(Customer customer) {
			return new CustomerResponse(customer.getId(), customer.getFullName(), customer.getPhone(),
					customer.getEmail(), customer.getAddress(), customer.getInternalNotes(),
					BranchSummary.from(customer.getRegisteredBranch()), customer.getCreatedAt(), customer.getUpdatedAt(),
					customer.getVersion());
		}

	}

	/** Why a customer was reported as a possible duplicate or match. */
	public enum MatchReason {
		PHONE, NAME, PHONE_AND_NAME
	}

	/**
	 * Minimal identity of a possible duplicate: id and name only. A customer outside the caller's
	 * scope appears here only through an exact phone match (whoever asks already knows the number:
	 * the customer is at the counter). {@code inScope} tells the UI whether the full record is
	 * already visible to the caller.
	 */
	public record CustomerMatch(long id, String fullName, boolean inScope, MatchReason matchedBy) {
	}

	/**
	 * A result of the incremental lookup. In-scope customers carry their contact data (the caller
	 * may read them anyway); a customer of another branch found by exact phone carries id and name
	 * only, and is used at reception with the phone as proof.
	 */
	public record CustomerCandidate(long id, String fullName, String phone, String email, BranchSummary registeredBranch,
			boolean inScope, boolean nameMatch, boolean phoneMatch) {

		static CustomerCandidate inScope(Customer customer, boolean nameMatch, boolean phoneMatch) {
			return new CustomerCandidate(customer.getId(), customer.getFullName(), customer.getPhone(),
					customer.getEmail(), BranchSummary.from(customer.getRegisteredBranch()), true, nameMatch,
					phoneMatch);
		}

		static CustomerCandidate outOfScope(Customer customer) {
			return new CustomerCandidate(customer.getId(), customer.getFullName(), null, null, null, false, false, true);
		}

	}

	/**
	 * Lookup answer: at most {@code size} candidates; {@code moreInScope} is how many more in-scope
	 * customers match (the UI asks to keep typing instead of paging through a dropdown).
	 */
	public record CustomerLookupResponse(List<CustomerCandidate> candidates, long moreInScope) {
	}

	/**
	 * Customer fields shared by the create request and the reception form's inline customer.
	 * {@code allowDuplicatePhone} is the explicit confirmation after a POSSIBLE_DUPLICATE_CUSTOMER
	 * answer; absent means false.
	 */
	public record NewCustomer(
			@NotBlank @Size(max = 160) String fullName,
			@NotBlank @Size(max = 30) String phone,
			@Email @Size(max = 254) String email,
			@Size(max = 300) String address,
			@Size(max = 1000) String internalNotes,
			Boolean allowDuplicatePhone,
			@Valid ConsentGrant consent) {
	}

	/**
	 * Channels the customer explicitly accepted while being registered (reception, customer form).
	 * Absent or empty = no consent; providing an e-mail or phone never implies one.
	 */
	public record ConsentGrant(
			@NotNull @Size(max = 2) List<@NotNull ContactChannel> channels,
			@NotNull ConsentSource source,
			@NotBlank @Size(max = 20) String textVersion) {
	}

	/** Grant or withdraw one channel (customer detail). {@code textVersion} is required to grant. */
	public record RecordConsentRequest(
			@NotNull ContactChannel channel,
			@NotNull Boolean granted,
			@NotNull ConsentSource source,
			@Size(max = 20) String textVersion) {
	}

	public record PersonRef(long id, String fullName) {
	}

	/** Current contact data of a customer, for system processes (notification delivery). */
	public record CustomerContact(long id, String fullName, String phone, String email) {
	}

	public record ConsentStatement(long id, ContactChannel channel, boolean granted, ConsentSource source,
			String textVersion, String reference, Instant statedAt, PersonRef recordedBy, Instant recordedAt) {
	}

	/** Current decision of one channel: the latest statement, or null when the customer never answered. */
	public record ConsentState(ContactChannel channel, ConsentStatement latest) {
	}

	public record CustomerConsents(long customerId, boolean hasEmail, String currentTextVersion,
			List<ConsentState> channels, List<ConsentStatement> history) {
	}

	public record CreateCustomerRequest(@NotNull Long branchId, @NotNull @Valid NewCustomer customer) {
	}

	public record UpdateCustomerRequest(
			@NotBlank @Size(max = 160) String fullName,
			@NotBlank @Size(max = 30) String phone,
			@Email @Size(max = 254) String email,
			@Size(max = 300) String address,
			@Size(max = 1000) String internalNotes,
			@NotNull Long version) {
	}

}
