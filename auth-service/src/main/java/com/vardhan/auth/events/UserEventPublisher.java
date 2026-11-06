package com.vardhan.auth.events;

import com.vardhan.common.events.EventTopology;
import com.vardhan.common.events.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends UserRegisteredEvent to RabbitMQ only AFTER the user row is committed.
 *
 * Known trade-off: if RabbitMQ is down at that exact moment the event is lost.
 * The production fix is the transactional outbox pattern (write the event to an
 * outbox table in the same transaction and relay it), noted in the README roadmap.
 */
@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public UserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        rabbitTemplate.convertAndSend(EventTopology.EXCHANGE, EventTopology.USER_REGISTERED, event);
        log.info("Published {} for userId={}", EventTopology.USER_REGISTERED, event.userId());
    }
}
