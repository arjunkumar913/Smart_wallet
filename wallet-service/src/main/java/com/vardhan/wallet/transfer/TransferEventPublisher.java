package com.vardhan.wallet.transfer;

import com.vardhan.common.events.EventTopology;
import com.vardhan.common.events.TransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes TransferCompleted only AFTER the transfer commits, so nobody is notified about money
 * that never moved. (Same at-most-once trade-off as auth-service; the outbox pattern is the fix.)
 */
@Component
public class TransferEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransferEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public TransferEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransferCompleted(TransferCompletedEvent event) {
        rabbitTemplate.convertAndSend(EventTopology.EXCHANGE, EventTopology.TRANSFER_COMPLETED, event);
        log.info("Published {} transactionId={}", EventTopology.TRANSFER_COMPLETED, event.transactionId());
    }
}
