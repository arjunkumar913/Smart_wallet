package com.vardhan.wallet.topup;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vardhan.wallet.payment.RazorpayGateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param checkout what the browser needs to open the payment page; present only while payment is awaited
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TopUpView(UUID topUpId, String status, BigDecimal amount, String currency, String provider,
                        String paymentMethod, UUID transactionId, String failureReason,
                        Instant createdAt, Instant completedAt, Checkout checkout) {

    /**
     * @param keyId         the gateway's public key (never the secret); null for the demo bank
     * @param amountInPaise the amount in the gateway's smallest unit
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Checkout(String keyId, String orderId, long amountInPaise) {
    }

    public static TopUpView from(TopUp t, String checkoutKeyId) {
        Checkout checkout = t.isAwaitingPayment()
                ? new Checkout(checkoutKeyId, t.getProviderOrderId(), RazorpayGateway.toMinorUnits(t.getAmount()))
                : null;
        return new TopUpView(t.getId(), t.getStatus().name(), t.getAmount(), t.getCurrency(), t.getProvider(),
                t.getPaymentMethod(), t.getTransactionId(), t.getFailureReason(),
                t.getCreatedAt(), t.getCompletedAt(), checkout);
    }
}
