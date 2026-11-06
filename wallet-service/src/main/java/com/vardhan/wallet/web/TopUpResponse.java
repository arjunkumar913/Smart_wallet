package com.vardhan.wallet.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TopUpResponse(UUID transactionId, Long walletId, BigDecimal amount,
                            BigDecimal balanceAfter, Instant createdAt) {
}
