package com.vardhan.wallet.payment;

/**
 * @param method how the customer paid (upi, card, netbanking...); may be null
 */
public record GatewayPayment(String paymentId, String method) {
}
