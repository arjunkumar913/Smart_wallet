package com.vardhan.smartwallet.transfer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Verifies that retries with the same Idempotency-Key never move money twice.
 */
class TransferIdempotencyTest {

    @Test
    @Disabled("TODO: implement on Day 2")
    void sameKeyTwice_debitsOnce_andReturnsOriginalResponse() {
        // TODO
    }

    @Test
    @Disabled("TODO: implement on Day 2")
    void sameKeyDifferentPayload_returns409() {
        // TODO
    }
}
