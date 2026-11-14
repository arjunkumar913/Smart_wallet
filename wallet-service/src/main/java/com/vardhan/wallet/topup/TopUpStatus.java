package com.vardhan.wallet.topup;

/**
 * CREATED  - row exists, the gateway order is still being created
 * PENDING  - gateway order exists, waiting for the customer to pay
 * SUCCESS  - paid and credited to the wallet (the only final state)
 * FAILED   - the gateway reported a failed payment attempt
 * EXPIRED  - nobody paid within the time limit
 *
 * FAILED and EXPIRED can still move to SUCCESS: a gateway lets the customer retry on the same order,
 * and money that was really collected must always be credited.
 */
public enum TopUpStatus {
    CREATED,
    PENDING,
    SUCCESS,
    FAILED,
    EXPIRED
}
