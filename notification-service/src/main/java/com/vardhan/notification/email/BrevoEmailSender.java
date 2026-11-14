package com.vardhan.notification.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Sends through Brevo's transactional email API over HTTPS (POST /v3/smtp/email).
 * HTTPS rather than SMTP because many free hosts, Render included, block outgoing SMTP ports.
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "brevo")
public class BrevoEmailSender implements EmailSender {

    static final String BASE_URL = "https://api.brevo.com";

    private final RestClient http;
    private final EmailProperties properties;

    public BrevoEmailSender(RestClient.Builder builder, EmailProperties properties) {
        String apiKey = properties.brevo() == null ? null : properties.brevo().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("EMAIL_PROVIDER=brevo needs BREVO_API_KEY");
        }
        if (properties.fromAddress() == null || properties.fromAddress().isBlank()) {
            throw new IllegalStateException("EMAIL_PROVIDER=brevo needs EMAIL_FROM_ADDRESS (a sender verified in Brevo)");
        }
        this.properties = properties;
        this.http = builder.baseUrl(BASE_URL)
                .defaultHeader("api-key", apiKey)
                .build();
    }

    @Override
    public String send(EmailMessage message) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", properties.fromName(), "email", properties.fromAddress()),
                "to", List.of(Map.of("email", message.to())),
                "subject", message.subject(),
                "textContent", message.textBody(),
                "htmlContent", message.htmlBody());
        try {
            Map<?, ?> response = http.post()
                    .uri("/v3/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Object id = response == null ? null : response.get("messageId");
            return id == null ? "brevo-unknown" : id.toString();
        } catch (RestClientResponseException e) {
            throw new EmailSendException("Brevo answered " + e.getStatusCode().value() + ": "
                    + abbreviate(e.getResponseBodyAsString()), isRetryable(e.getStatusCode()), e);
        } catch (ResourceAccessException e) {
            // timeout or connection failure: the provider may be down for a moment
            throw new EmailSendException("Could not reach Brevo: " + e.getMessage(), true, e);
        }
    }

    /** Rate limits and server errors pass; anything else 4xx (bad key, bad address, bad request) will fail again. */
    static boolean isRetryable(HttpStatusCode status) {
        return status.value() == 429 || status.value() == 408 || status.is5xxServerError();
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "…";
    }
}
