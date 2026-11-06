package com.vardhan.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by wallet-service after a transfer commits; notification-service notifies both parties.
 */
public record TransferCompletedEvent(
        UUID eventId,
        UUID transactionId,
        Long fromUserId,
        String fromEmail,
        Long toUserId,
        String toEmail,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {
}
