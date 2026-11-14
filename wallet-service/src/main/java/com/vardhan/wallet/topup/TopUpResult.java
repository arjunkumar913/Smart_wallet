package com.vardhan.wallet.topup;

/**
 * @param replayed true when this is the top-up created by an earlier request with the same Idempotency-Key
 */
public record TopUpResult(TopUpView topUp, boolean replayed) {
}
