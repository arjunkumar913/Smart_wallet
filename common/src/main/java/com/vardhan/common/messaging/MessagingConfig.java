package com.vardhan.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.vardhan.common.events.EventTopology.DEAD_LETTER_EXCHANGE;
import static com.vardhan.common.events.EventTopology.EXCHANGE;
import static com.vardhan.common.events.EventTopology.NOTIFICATION_TRANSFER_COMPLETED_DLQ;
import static com.vardhan.common.events.EventTopology.NOTIFICATION_TRANSFER_COMPLETED_QUEUE;
import static com.vardhan.common.events.EventTopology.TRANSFER_COMPLETED;
import static com.vardhan.common.events.EventTopology.USER_REGISTERED;
import static com.vardhan.common.events.EventTopology.WALLET_USER_REGISTERED_DLQ;
import static com.vardhan.common.events.EventTopology.WALLET_USER_REGISTERED_QUEUE;

/**
 * Shared RabbitMQ setup used by every service.
 *
 * The WHOLE topology (exchanges, every consumer queue, dead-letter queues, bindings) is declared
 * by whichever service connects first. Before this, a queue only existed once its consumer had started,
 * so an event published earlier (e.g. the admin's UserRegistered at auth-service startup) had no
 * queue to land in and RabbitMQ silently dropped it. Declarations are idempotent, so every service
 * declaring the same topology is safe.
 */
@Configuration
public class MessagingConfig {

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.vardhan.common.events");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public Declarables eventTopology() {
        TopicExchange events = new TopicExchange(EXCHANGE, true, false);
        TopicExchange deadLetters = new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);

        Queue userRegistered = queueWithDeadLetter(WALLET_USER_REGISTERED_QUEUE, WALLET_USER_REGISTERED_DLQ);
        Queue userRegisteredDlq = QueueBuilder.durable(WALLET_USER_REGISTERED_DLQ).build();
        Queue transferCompleted = queueWithDeadLetter(NOTIFICATION_TRANSFER_COMPLETED_QUEUE, NOTIFICATION_TRANSFER_COMPLETED_DLQ);
        Queue transferCompletedDlq = QueueBuilder.durable(NOTIFICATION_TRANSFER_COMPLETED_DLQ).build();

        return new Declarables(
                events, deadLetters,
                userRegistered, userRegisteredDlq, transferCompleted, transferCompletedDlq,
                BindingBuilder.bind(userRegistered).to(events).with(USER_REGISTERED),
                BindingBuilder.bind(userRegisteredDlq).to(deadLetters).with(WALLET_USER_REGISTERED_DLQ),
                BindingBuilder.bind(transferCompleted).to(events).with(TRANSFER_COMPLETED),
                BindingBuilder.bind(transferCompletedDlq).to(deadLetters).with(NOTIFICATION_TRANSFER_COMPLETED_DLQ));
    }

    private static Queue queueWithDeadLetter(String name, String deadLetterRoutingKey) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }
}
