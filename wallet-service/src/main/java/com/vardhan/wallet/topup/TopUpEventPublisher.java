package com.vardhan.wallet.topup;

import com.vardhan.common.events.EventTopology;
import com.vardhan.common.events.TopUpCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes TopUpCompleted only AFTER the credit commits, so nobody is told about money that
 * never arrived. (Same at-most-once trade-off as transfers; the outbox pattern is the fix.)
 */
@Component
public class TopUpEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TopUpEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public TopUpEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTopUpCompleted(TopUpCompletedEvent event) {
        rabbitTemplate.convertAndSend(EventTopology.EXCHANGE, EventTopology.TOP_UP_COMPLETED, event);
        log.info("Published {} topUpId={}", EventTopology.TOP_UP_COMPLETED, event.topUpId());
    }
}
