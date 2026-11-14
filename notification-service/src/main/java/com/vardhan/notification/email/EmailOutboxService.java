package com.vardhan.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Queues an email in the caller's transaction, next to the in-app notification. Both are committed
 * together or not at all, so an email is never queued for a notification that was rolled back,
 * and a notification never exists without its email queued.
 */
@Service
public class EmailOutboxService {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxService.class);

    private final EmailOutboxRepository repository;
    private final EmailProperties properties;

    public EmailOutboxService(EmailOutboxRepository repository, EmailProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** @return true if an email was queued */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean enqueue(UUID eventId, Long userId, EmailMessage message) {
        if (properties.shouldSkip(message.to())) {
            log.debug("No email for eventId={} userId={}: address {} is on a skipped domain", eventId, userId, message.to());
            return false;
        }
        if (repository.existsByEventIdAndUserId(eventId, userId)) {
            return false;   // redelivered event; the unique index (event_id, user_id) backs this up
        }
        repository.save(new EmailOutbox(eventId, userId, message));
        return true;
    }
}
