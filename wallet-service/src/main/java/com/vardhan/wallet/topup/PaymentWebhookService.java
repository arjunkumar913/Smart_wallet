package com.vardhan.wallet.topup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vardhan.common.web.BusinessException;
import com.vardhan.wallet.payment.GatewayPayment;
import com.vardhan.wallet.payment.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Handles the gateway's server-to-server messages (Razorpay's webhook format).
 *
 * Rule 1: verify the signature over the RAW body before believing anything in it.
 * Rule 2: be idempotent. Gateways deliver at-least-once and retry on any non-2xx answer,
 *         so the same event can arrive many times; settlement makes repeats harmless.
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentGateway gateway;
    private final TopUpSettlementService settlement;
    private final ObjectMapper objectMapper;

    public PaymentWebhookService(PaymentGateway gateway, TopUpSettlementService settlement, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.settlement = settlement;
        this.objectMapper = objectMapper;
    }

    public void handle(byte[] rawBody, String signature) {
        if (!gateway.isValidWebhookSignature(rawBody, signature)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "Signature verification failed");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Webhook body is not valid JSON");
        }

        String event = root.path("event").asText("");
        JsonNode payment = root.path("payload").path("payment").path("entity");
        String orderId = payment.path("order_id").asText(null);
        String paymentId = payment.path("id").asText(null);

        switch (event) {
            case "payment.captured", "order.paid" -> {
                if (orderId == null || paymentId == null) {
                    log.warn("Webhook {} without order or payment id; ignored", event);
                    return;
                }
                Long paidPaise = payment.hasNonNull("amount") ? payment.get("amount").asLong() : null;
                settlement.settleSuccess(orderId, new GatewayPayment(paymentId, payment.path("method").asText(null)), paidPaise);
            }
            case "payment.failed" -> {
                if (orderId != null) {
                    settlement.markFailed(orderId, payment.path("error_description").asText("The payment failed"));
                }
            }
            // Answer 200 to everything else, otherwise the gateway keeps retrying events we do not use.
            default -> log.debug("Webhook event {} ignored", event);
        }
    }
}
