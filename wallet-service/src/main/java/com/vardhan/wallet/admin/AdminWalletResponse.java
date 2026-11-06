package com.vardhan.wallet.admin;

import com.vardhan.wallet.wallet.Wallet;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminWalletResponse(Long walletId, Long userId, String email, String type, BigDecimal balance,
                                  String currency, String status, Instant createdAt) {

    public static AdminWalletResponse from(Wallet w) {
        return new AdminWalletResponse(w.getId(), w.getUserId(), w.getEmail(), w.getWalletType().name(),
                w.getBalance(), w.getCurrency(), w.getStatus().name(), w.getCreatedAt());
    }
}
