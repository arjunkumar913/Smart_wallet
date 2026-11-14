package com.vardhan.notification.email;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    boolean existsByEventIdAndUserId(UUID eventId, Long userId);

    List<EmailOutbox> findByEventIdOrderByIdAsc(UUID eventId);
}
