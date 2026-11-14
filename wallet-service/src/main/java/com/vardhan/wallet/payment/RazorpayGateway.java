package com.vardhan.wallet.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Razorpay over its REST API (no SDK): Orders API to start a payment, HMAC-SHA256 to verify what comes back.
 * Works the same with test keys (rzp_test_...) and live keys.
 */
@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "razorpay")
public class RazorpayGateway implements PaymentGateway {

    private final RestClient client;
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;

    public RazorpayGateway(RestClient.Builder builder,
                           @Value("${app.payments.razorpay.base-url:https://api.razorpay.com}") String baseUrl,
                           @Value("${app.payments.razorpay.key-id:}") String keyId,
                           @Value("${app.payments.razorpay.key-secret:}") String keySecret,
                           @Value("${app.payments.razorpay.webhook-secret:}") String webhookSecret) {
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new IllegalStateException(
                    "app.payments.provider=razorpay needs RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET");
        }
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        this.client = builder
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> headers.setBasicAuth(keyId, keySecret))
                .build();
    }

    @Override
    public String provider() {
        return "RAZORPAY";
    }

    @Override
    public String checkoutKeyId() {
        return keyId;
    }

    @Override
    public GatewayOrder createOrder(UUID topUpId, BigDecimal amount, String currency) {
        Map<String, Object> body = Map.of(
                "amount", toMinorUnits(amount),          // Razorpay amounts are in paise
                "currency", currency,
                "receipt", topUpId.toString(),
                "notes", Map.of("topUpId", topUpId.toString()));
        try {
            JsonNode order = client.post().uri("/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (order == null || !order.hasNonNull("id")) {
                throw new PaymentGatewayException("Razorpay returned an order without an id", null);
            }
            return new GatewayOrder(order.get("id").asText());
        } catch (RestClientException e) {
            throw new PaymentGatewayException("Could not create a Razorpay order", e);
        }
    }

    /** Razorpay signs "order_id|payment_id" with the key secret and gives the result to the browser. */
    @Override
    public boolean isValidPaymentSignature(String orderId, String paymentId, String signature) {
        byte[] signed = (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8);
        return HmacSha256.matches(keySecret, signed, signature);
    }

    /** Webhooks are signed over the raw request body with the webhook secret set in the dashboard. */
    @Override
    public boolean isValidWebhookSignature(byte[] body, String signature) {
        return !webhookSecret.isBlank() && HmacSha256.matches(webhookSecret, body, signature);
    }

    @Override
    public Optional<GatewayPayment> findSuccessfulPayment(String orderId) {
        try {
            JsonNode payments = client.get().uri("/v1/orders/{orderId}/payments", orderId)
                    .retrieve()
                    .body(JsonNode.class);
            if (payments == null) {
                return Optional.empty();
            }
            for (JsonNode payment : payments.path("items")) {
                if ("captured".equals(payment.path("status").asText())) {
                    return Optional.of(new GatewayPayment(payment.path("id").asText(),
                            payment.path("method").asText(null)));
                }
            }
            return Optional.empty();
        } catch (RestClientException e) {
            throw new PaymentGatewayException("Could not fetch payments for Razorpay order " + orderId, e);
        }
    }

    public static long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }
}
