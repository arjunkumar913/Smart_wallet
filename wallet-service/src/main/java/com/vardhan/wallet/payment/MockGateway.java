package com.vardhan.wallet.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A built-in demo bank, used when no real gateway is configured (the default).
 *
 * It behaves like a real gateway from the wallet's point of view: it issues order ids, signs its
 * webhooks with HMAC-SHA256, and can be asked whether an order was paid. Only the customer-facing
 * part is simulated (see MockPaymentController). Nothing here is trusted more than Razorpay is:
 * the same signature checks and the same settlement code run for both.
 */
@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "mock", matchIfMissing = true)
public class MockGateway implements PaymentGateway {

    /** Generated per start-up: the demo bank and the wallet live in one process, so nothing has to be shared. */
    private final String secret = randomHex(32);

    /** Orders the demo bank considers paid, for reconciliation: orderId -> payment. */
    private final Map<String, GatewayPayment> paidOrders = new ConcurrentHashMap<>();

    @Override
    public String provider() {
        return "MOCK";
    }

    @Override
    public String checkoutKeyId() {
        return null;
    }

    @Override
    public GatewayOrder createOrder(UUID topUpId, BigDecimal amount, String currency) {
        return new GatewayOrder("order_mock_" + randomHex(8));
    }

    @Override
    public boolean isValidPaymentSignature(String orderId, String paymentId, String signature) {
        return HmacSha256.matches(secret, (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8), signature);
    }

    @Override
    public boolean isValidWebhookSignature(byte[] body, String signature) {
        return HmacSha256.matches(secret, body, signature);
    }

    @Override
    public Optional<GatewayPayment> findSuccessfulPayment(String orderId) {
        return Optional.ofNullable(paidOrders.get(orderId));
    }

    // ---- the "bank side", used only by the demo payment page and by tests ----

    public String newPaymentId() {
        return "pay_mock_" + randomHex(8);
    }

    public void recordPaid(String orderId, GatewayPayment payment) {
        paidOrders.put(orderId, payment);
    }

    public String signWebhook(byte[] body) {
        return HmacSha256.hex(secret, body);
    }

    public String signPayment(String orderId, String paymentId) {
        return HmacSha256.hex(secret, (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
    }

    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        new SecureRandom().nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }
}
