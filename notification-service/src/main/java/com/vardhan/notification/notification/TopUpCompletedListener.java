package com.vardhan.notification.notification;

import com.vardhan.common.events.EventTopology;
import com.vardhan.common.events.TopUpCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells the user their money arrived. Idempotent the same way as transfer notifications:
 * (event_id, user_id) is unique, so a redelivered event creates nothing new.
 */
@Component
public class TopUpCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(TopUpCompletedListener.class);

    private final NotificationRepository repository;

    public TopUpCompletedListener(NotificationRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = EventTopology.NOTIFICATION_TOP_UP_COMPLETED_QUEUE)
    @Transactional
    public void onTopUpCompleted(TopUpCompletedEvent event) {
        if (repository.existsByEventIdAndUserId(event.eventId(), event.userId())) {
            log.info("Duplicate {} eventId={} ignored", EventTopology.TOP_UP_COMPLETED, event.eventId());
            return;
        }
        String amount = TransferCompletedListener.formatAmount(event.amount(), event.currency());
        repository.save(new Notification(event.eventId(), event.userId(), amount + " was added to your wallet"));
    }
}
