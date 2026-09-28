package dev.jeffrojas.electronicarojas.notifications;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, Long> {

	List<NotificationAttempt> findByOutboxIdOrderByIdAsc(long outboxId);

}
