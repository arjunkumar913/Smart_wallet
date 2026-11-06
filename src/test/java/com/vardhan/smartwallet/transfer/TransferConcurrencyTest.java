package com.vardhan.smartwallet.transfer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Integration test (Testcontainers PostgreSQL) proving transfers stay consistent under concurrency.
 */
class TransferConcurrencyTest {

    @Test
    @Disabled("TODO: implement on Day 2")
    void hundredParallelTransfers_keepTotalBalanceConstant() {
        // TODO: seed two wallets, fire 100 transfers in both directions via ExecutorService,
        //       assert no deadlock, no negative balance, and sum of balances unchanged
    }
}
