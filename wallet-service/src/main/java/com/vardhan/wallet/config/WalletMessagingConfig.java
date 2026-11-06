package com.vardhan.wallet.config;

import com.vardhan.common.events.EventTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queues owned by wallet-service. A message that still fails after the listener retries
 * is routed to the dead-letter queue instead of being retried forever.
 */
@Configuration
public class WalletMessagingConfig {

    public static final String USER_REGISTERED_QUEUE = "wallet.user-registered.queue";
    public static final String USER_REGISTERED_DLQ = "wallet.user-registered.dlq";

    @Bean
    public Queue userRegisteredQueue() {
        return QueueBuilder.durable(USER_REGISTERED_QUEUE)
                .deadLetterExchange(EventTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(USER_REGISTERED_DLQ)
                .build();
    }

    @Bean
    public Queue userRegisteredDeadLetterQueue() {
        return QueueBuilder.durable(USER_REGISTERED_DLQ).build();
    }

    @Bean
    public Binding userRegisteredBinding(@Qualifier("userRegisteredQueue") Queue queue,
                                         @Qualifier("walletEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(EventTopology.USER_REGISTERED);
    }

    @Bean
    public Binding userRegisteredDeadLetterBinding(@Qualifier("userRegisteredDeadLetterQueue") Queue queue,
                                                   @Qualifier("deadLetterExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(USER_REGISTERED_DLQ);
    }
}
