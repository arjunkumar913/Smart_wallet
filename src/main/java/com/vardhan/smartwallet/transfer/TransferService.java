package com.vardhan.smartwallet.transfer;

/**
 * Core money movement. See README transfer flow.
 */
public class TransferService {
    // TODO: Check Idempotency-Key; return stored response on replay
    // TODO: Lock both wallets FOR UPDATE in ascending id order
    // TODO: Validate: funds, max-amount, self-transfer, frozen
    // TODO: Write DEBIT + CREDIT entries, update balances
    // TODO: Save idempotency record, commit
    // TODO: Publish TransferCompleted AFTER commit (@TransactionalEventListener)
}
