package com.vardhan.smartwallet.idempotency;

/**
 * Stored idempotency key + response (table: idempotency_keys).
 */
public class IdempotencyRecord {
    // TODO: Unique on (user_id, idempotency_key)
    // TODO: Reject same key with different request_hash (409)
}
