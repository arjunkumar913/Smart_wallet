package com.vardhan.wallet.transfer;

/**
 * @param replayed true when this is the stored answer to an earlier request with the same Idempotency-Key
 */
public record TransferResult(TransferResponse response, boolean replayed) {
}
