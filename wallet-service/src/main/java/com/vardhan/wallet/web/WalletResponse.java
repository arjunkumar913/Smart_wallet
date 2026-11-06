package com.vardhan.wallet.web;

import com.vardhan.wallet.wallet.Wallet;

import java.math.BigDecimal;

public record WalletResponse(Long walletId, String email, BigDecimal balance, String currency, String status) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getEmail(), wallet.getBalance(),
                wallet.getCurrency(), wallet.getStatus().name());
    }
}
