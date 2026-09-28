package dev.jeffrojas.electronicarojas.customers;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CustomerConsentRepository extends JpaRepository<CustomerConsent, Long> {

	/** The current decision: the latest statement for the channel (none = never consented). */
	Optional<CustomerConsent> findFirstByCustomerIdAndChannelOrderByStatedAtDescIdDesc(long customerId,
			ContactChannel channel);

	@Query("""
			select c from CustomerConsent c join fetch c.recordedBy
			where c.customer.id = :customerId order by c.statedAt desc, c.id desc""")
	List<CustomerConsent> findHistory(long customerId);

}
