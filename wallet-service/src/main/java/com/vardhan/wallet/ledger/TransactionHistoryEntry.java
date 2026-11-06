package com.vardhan.wallet.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param direction DEBIT = money left the wallet, CREDIT = money arrived
 */
public record TransactionHistoryEntry(UUID transactionId, String direction, BigDecimal amount,
                                      BigDecimal balanceAfter, String description, Instant createdAt) {

    public static TransactionHistoryEntry from(LedgerEntry e) {
        return new TransactionHistoryEntry(e.getTransactionId(), e.getEntryType().name(), e.getAmount(),
                e.getBalanceAfter(), e.getDescription(), e.getCreatedAt());
    }
}
