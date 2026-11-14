package com.vardhan.notification.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {

    /** A slow provider must not hold a dispatcher (and its claimed emails) for long. */
    @Bean
    @ConditionalOnProperty(name = "app.email.provider", havingValue = "brevo")
    RestClientCustomizer emailHttpTimeouts() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(5));
            factory.setReadTimeout(Duration.ofSeconds(10));
            builder.requestFactory(factory);
        };
    }

    /** Separate from the dispatcher so tests can switch the timer off and drive dispatch by hand. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "app.email.dispatch.enabled", havingValue = "true", matchIfMissing = true)
    static class EmailSchedulingConfig {

        @Bean
        EmailDispatchScheduler emailDispatchScheduler(EmailDispatcher dispatcher) {
            return new EmailDispatchScheduler(dispatcher);
        }
    }
}
