package com.vardhan.wallet.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param balanceAfter the SENDER's balance after the transfer
 */
public record TransferResponse(UUID transactionId, Long fromWalletId, Long toWalletId, String toEmail,
                               BigDecimal amount, String currency, BigDecimal balanceAfter,
                               String note, Instant createdAt) {
}
