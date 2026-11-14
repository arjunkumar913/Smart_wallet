package com.vardhan.notification.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class BrevoEmailSenderTest {

    private MockRestServiceServer brevo;
    private BrevoEmailSender sender;
    private final EmailMessage message = new EmailMessage("friend@test.com", "You received ₹320.00", "plain", "<p>html</p>");

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        brevo = MockRestServiceServer.bindTo(builder).build();
        sender = new BrevoEmailSender(builder, TestEmailProperties.defaults());
    }

    @Test
    void sendsTheEmailAsBrevoExpects_andReturnsItsMessageId() {
        brevo.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sender.email").value("no-reply@smartwallet.dev"))
                .andExpect(jsonPath("$.sender.name").value("Smart Wallet"))
                .andExpect(jsonPath("$.to[0].email").value("friend@test.com"))
                .andExpect(jsonPath("$.subject").value("You received ₹320.00"))
                .andExpect(jsonPath("$.textContent").value("plain"))
                .andExpect(jsonPath("$.htmlContent").value("<p>html</p>"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"messageId\":\"<202611140713.123@smtp-relay.mailin.fr>\"}"));

        assertThat(sender.send(message)).isEqualTo("<202611140713.123@smtp-relay.mailin.fr>");
        brevo.verify();
    }

    @Test
    void rejectedRequest_isNotRetried() {
        brevo.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"invalid_parameter\",\"message\":\"email is not valid\"}"));

        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOfSatisfying(EmailSendException.class, e -> {
                    assertThat(e.isRetryable()).isFalse();
                    assertThat(e.getMessage()).contains("400", "email is not valid");
                });
    }

    @Test
    void badApiKey_isNotRetried() {
        brevo.expect(requestTo("https://api.brevo.com/v3/smtp/email")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOfSatisfying(EmailSendException.class, e -> assertThat(e.isRetryable()).isFalse());
    }

    @Test
    void rateLimitAndServerErrors_areRetried() {
        brevo.expect(requestTo("https://api.brevo.com/v3/smtp/email")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        brevo.expect(requestTo("https://api.brevo.com/v3/smtp/email")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOfSatisfying(EmailSendException.class, e -> assertThat(e.isRetryable()).isTrue());
        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOfSatisfying(EmailSendException.class, e -> assertThat(e.isRetryable()).isTrue());
    }

    @Test
    void timeout_isRetried() {
        brevo.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withException((IOException) new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOfSatisfying(EmailSendException.class, e -> {
                    assertThat(e.isRetryable()).isTrue();
                    assertThat(e.getMessage()).contains("Could not reach Brevo");
                });
    }

    @Test
    void missingApiKey_failsAtStartup() {
        EmailProperties noKey = new EmailProperties("brevo", "no-reply@smartwallet.dev", "Smart Wallet", "https://x",
                java.time.ZoneId.of("Asia/Kolkata"), java.util.List.of(), new EmailProperties.Brevo(""),
                TestEmailProperties.defaults().dispatch());

        assertThatThrownBy(() -> new BrevoEmailSender(RestClient.builder(), noKey))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("BREVO_API_KEY");
    }
}
