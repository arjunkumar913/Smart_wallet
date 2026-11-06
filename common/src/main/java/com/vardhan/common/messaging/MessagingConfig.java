package com.vardhan.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vardhan.common.events.EventTopology;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared RabbitMQ setup: events travel as JSON, and both exchanges are declared by whichever service starts first.
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
    public TopicExchange walletEventsExchange() {
        return new TopicExchange(EventTopology.EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(EventTopology.DEAD_LETTER_EXCHANGE, true, false);
    }
}
