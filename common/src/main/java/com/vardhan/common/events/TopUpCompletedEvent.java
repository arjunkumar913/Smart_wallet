package com.vardhan.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by wallet-service after a paid top-up is credited; notification-service tells the user.
 *
 * @param provider the payment gateway that collected the money, e.g. RAZORPAY
 */
public record TopUpCompletedEvent(
        UUID eventId,
        UUID topUpId,
        UUID transactionId,
        Long userId,
        String email,
        BigDecimal amount,
        String currency,
        String provider,
        Instant occurredAt) {
}
