package com.vardhan.wallet.payment;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class PaymentHttpConfig {

    /** A payment page must never hang on a slow gateway: fail fast and let the user retry. */
    @Bean
    public RestClientCustomizer gatewayTimeouts() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(5));
            factory.setReadTimeout(Duration.ofSeconds(10));
            builder.requestFactory(factory);
        };
    }
}
