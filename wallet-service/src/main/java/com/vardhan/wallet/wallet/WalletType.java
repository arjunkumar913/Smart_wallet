package com.vardhan.wallet.wallet;

/**
 * USER wallets belong to people and can never go negative.
 * The single SYSTEM wallet is the funding source for top-ups; it goes negative by design,
 * so the sum of ALL wallet balances is always exactly zero (a classic double-entry invariant).
 */
public enum WalletType {
    USER,
    SYSTEM
}
