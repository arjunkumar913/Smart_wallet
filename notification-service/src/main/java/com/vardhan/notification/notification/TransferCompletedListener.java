package com.vardhan.notification.notification;

import com.vardhan.common.events.EventTopology;
import com.vardhan.common.events.TransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Turns each TransferCompleted event into two notifications: one for the sender, one for the recipient.
 *
 * Idempotent: (event_id, user_id) is unique, so a redelivered event creates nothing new.
 * If processing keeps failing, the listener retries 3 times and then RabbitMQ moves the
 * message to notification.transfer-completed.dlq for inspection instead of blocking the queue.
 */
@Component
public class TransferCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(TransferCompletedListener.class);

    private final NotificationRepository repository;

    public TransferCompletedListener(NotificationRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = EventTopology.NOTIFICATION_TRANSFER_COMPLETED_QUEUE)
    @Transactional
    public void onTransferCompleted(TransferCompletedEvent event) {
        String amount = formatAmount(event.amount(), event.currency());
        int created = 0;
        created += notifyOnce(event.eventId(), event.fromUserId(), "You sent " + amount + " to " + event.toEmail());
        created += notifyOnce(event.eventId(), event.toUserId(), "You received " + amount + " from " + event.fromEmail());
        if (created == 0) {
            log.info("Duplicate {} eventId={} ignored", EventTopology.TRANSFER_COMPLETED, event.eventId());
        }
    }

    private int notifyOnce(UUID eventId, Long userId, String message) {
        if (repository.existsByEventIdAndUserId(eventId, userId)) {
            return 0;
        }
        repository.save(new Notification(eventId, userId, message));
        return 1;
    }

    static String formatAmount(BigDecimal amount, String currency) {
        String value = amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        return "INR".equals(currency) ? "₹" + value : currency + " " + value;
    }
}
