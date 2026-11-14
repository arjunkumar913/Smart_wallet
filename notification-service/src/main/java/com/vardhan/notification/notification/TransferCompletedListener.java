package com.vardhan.notification.notification;

import com.vardhan.common.events.EventTopology;
import com.vardhan.common.events.TransferCompletedEvent;
import com.vardhan.notification.email.EmailComposer;
import com.vardhan.notification.email.EmailOutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Turns each TransferCompleted event into two notifications, one for the sender and one for the recipient,
 * and queues an email to each of them in the same transaction.
 *
 * Idempotent: (event_id, user_id) is unique for both notifications and emails, so a redelivered event
 * creates and sends nothing new. If processing keeps failing, the listener retries 3 times and then RabbitMQ
 * moves the message to notification.transfer-completed.dlq for inspection instead of blocking the queue.
 */
@Component
public class TransferCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(TransferCompletedListener.class);

    private final NotificationRepository repository;
    private final EmailOutboxService emails;
    private final EmailComposer composer;

    public TransferCompletedListener(NotificationRepository repository, EmailOutboxService emails, EmailComposer composer) {
        this.repository = repository;
        this.emails = emails;
        this.composer = composer;
    }

    @RabbitListener(queues = EventTopology.NOTIFICATION_TRANSFER_COMPLETED_QUEUE)
    @Transactional
    public void onTransferCompleted(TransferCompletedEvent event) {
        String amount = formatAmount(event.amount(), event.currency());
        int created = 0;
        if (notifyOnce(event.eventId(), event.fromUserId(), "You sent " + amount + " to " + event.toEmail())) {
            created++;
            emails.enqueue(event.eventId(), event.fromUserId(), composer.transferSent(event.fromEmail(), event.toEmail(),
                    event.amount(), event.currency(), event.transactionId(), event.occurredAt()));
        }
        if (notifyOnce(event.eventId(), event.toUserId(), "You received " + amount + " from " + event.fromEmail())) {
            created++;
            emails.enqueue(event.eventId(), event.toUserId(), composer.transferReceived(event.toEmail(), event.fromEmail(),
                    event.amount(), event.currency(), event.transactionId(), event.occurredAt()));
        }
        if (created == 0) {
            log.info("Duplicate {} eventId={} ignored", EventTopology.TRANSFER_COMPLETED, event.eventId());
        }
    }

    private boolean notifyOnce(UUID eventId, Long userId, String message) {
        if (repository.existsByEventIdAndUserId(eventId, userId)) {
            return false;
        }
        repository.save(new Notification(eventId, userId, message));
        return true;
    }

    static String formatAmount(BigDecimal amount, String currency) {
        String value = amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        return "INR".equals(currency) ? "₹" + value : currency + " " + value;
    }
}
