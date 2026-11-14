package com.vardhan.notification;

import com.vardhan.notification.email.RecordingEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared setup for integration tests: one real PostgreSQL container and one Spring context for the whole run.
 * RabbitMQ listeners stay off (tests call the listeners directly), the email timer is off (tests run the
 * dispatcher by hand), and emails go to a {@link RecordingEmailSender} instead of a provider. Requires Docker.
 */
@SpringBootTest(properties = {  // default MOCK web environment, so the security config loads as in production
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false",
        "app.email.provider=test",
        "app.email.dispatch.enabled=false"
})
@Import(IntegrationTestBase.EmailTestConfig.class)
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected RecordingEmailSender emailSender;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM email_outbox");
        jdbc.update("DELETE FROM notifications");
        emailSender.reset();
    }

    @TestConfiguration
    static class EmailTestConfig {
        @Bean
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }
}
