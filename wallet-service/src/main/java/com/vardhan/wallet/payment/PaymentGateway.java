package com.vardhan.wallet.payment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * The bank side of a top-up. The wallet never talks to banks directly: a payment gateway collects the
 * money over UPI / cards / netbanking and tells us about it with signed messages.
 *
 * Two implementations, chosen by {@code app.payments.provider}:
 * {@link RazorpayGateway} (Razorpay test or live mode) and {@link MockGateway} (a built-in demo bank,
 * the default, so the project runs without any gateway account).
 */
public interface PaymentGateway {

    /** Stored on every top-up, e.g. RAZORPAY or MOCK. */
    String provider();

    /** The public key the browser needs to open the gateway's checkout; null when there is none. */
    String checkoutKeyId();

    /**
     * Registers the intent to collect {@code amount} and returns the gateway's order id.
     *
     * @throws PaymentGatewayException if the gateway cannot be reached or rejects the request
     */
    GatewayOrder createOrder(UUID topUpId, BigDecimal amount, String currency);

    /** Verifies the signature the checkout hands to the browser after a successful payment. */
    boolean isValidPaymentSignature(String orderId, String paymentId, String signature);

    /** Verifies that a webhook body really came from the gateway. */
    boolean isValidWebhookSignature(byte[] body, String signature);

    /**
     * Asks the gateway whether this order has been paid. Used by reconciliation when no webhook
     * or browser confirmation arrived.
     */
    Optional<GatewayPayment> findSuccessfulPayment(String orderId);
}
