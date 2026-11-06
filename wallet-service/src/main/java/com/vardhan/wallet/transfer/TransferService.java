package com.vardhan.wallet.transfer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vardhan.common.events.TransferCompletedEvent;
import com.vardhan.common.web.BusinessException;
import com.vardhan.wallet.idempotency.IdempotencyRecord;
import com.vardhan.wallet.idempotency.IdempotencyRecordRepository;
import com.vardhan.wallet.ledger.EntryType;
import com.vardhan.wallet.ledger.LedgerEntry;
import com.vardhan.wallet.ledger.LedgerEntryRepository;
import com.vardhan.wallet.wallet.Wallet;
import com.vardhan.wallet.wallet.WalletLocker;
import com.vardhan.wallet.wallet.WalletRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Peer-to-peer transfers. Everything below runs in ONE database transaction:
 * claim the idempotency key, lock both wallets, check the rules, move the money,
 * write the ledger pair, store the response. Any failure rolls all of it back.
 */
@Service
public class TransferService {

    private static final int MAX_KEY_LENGTH = 100;

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletLocker walletLocker;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final BigDecimal maxTransfer;

    public TransferService(WalletRepository walletRepository, LedgerEntryRepository ledgerEntryRepository,
                           WalletLocker walletLocker, IdempotencyRecordRepository idempotencyRepository,
                           ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper,
                           @Value("${app.wallet.max-transfer:100000.00}") BigDecimal maxTransfer) {
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.walletLocker = walletLocker;
        this.idempotencyRepository = idempotencyRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.maxTransfer = maxTransfer;
    }

    @Transactional
    public TransferResult transfer(Long senderUserId, String idempotencyKey, TransferRequest request) {
        String key = validateKey(idempotencyKey);
        String toEmail = request.toEmail().trim().toLowerCase(Locale.ROOT);
        BigDecimal amount = request.amount();
        String requestHash = fingerprint(toEmail, amount, request.note());

        // 1. Seen this key before? Return the original answer, move no money.
        Optional<IdempotencyRecord> previous = idempotencyRepository.findByUserIdAndIdempotencyKey(senderUserId, key);
        if (previous.isPresent()) {
            return replay(previous.get(), requestHash);
        }

        // 2. Cheap rule checks before taking any locks.
        if (amount.compareTo(maxTransfer) > 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TRANSFER_LIMIT_EXCEEDED",
                    "A single transfer cannot exceed " + maxTransfer);
        }
        Long fromWalletId = walletRepository.findIdByUserId(senderUserId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "You do not have a wallet yet"));
        Long toWalletId = walletRepository.findIdByEmail(toEmail)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "RECIPIENT_NOT_FOUND", "No wallet exists for " + toEmail));
        if (fromWalletId.equals(toWalletId)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SELF_TRANSFER", "You cannot transfer money to yourself");
        }

        // 3. Claim the key. A concurrent request with the same key blocks on the unique index here
        //    and fails once we commit, so two identical requests can never both move money.
        IdempotencyRecord record = claimKey(senderUserId, key, requestHash);

        // 4. Lock both wallets in id order (deadlock-free), then re-check state on the locked rows.
        Map<Long, Wallet> locked = walletLocker.lockInOrder(fromWalletId, toWalletId);
        Wallet from = locked.get(fromWalletId);
        Wallet to = locked.get(toWalletId);
        if (!from.isActive()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "WALLET_FROZEN", "Your wallet is frozen");
        }
        if (!to.isActive()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "RECIPIENT_WALLET_FROZEN", "The recipient's wallet is frozen");
        }

        // 5. Move the money: one DEBIT + one CREDIT sharing a transaction id.
        from.debit(amount);   // throws INSUFFICIENT_FUNDS, rolling everything back
        to.credit(amount);
        UUID transactionId = UUID.randomUUID();
        ledgerEntryRepository.save(new LedgerEntry(transactionId, from.getId(), EntryType.DEBIT, amount,
                from.getBalance(), "Transfer to " + to.getEmail()));
        ledgerEntryRepository.save(new LedgerEntry(transactionId, to.getId(), EntryType.CREDIT, amount,
                to.getBalance(), "Transfer from " + from.getEmail()));

        Instant now = Instant.now();
        TransferResponse response = new TransferResponse(transactionId, from.getId(), to.getId(), to.getEmail(),
                amount, from.getCurrency(), from.getBalance(), request.note(), now);

        // 6. Remember the answer for retries, and announce the transfer (sent only after commit).
        record.complete(HttpStatus.CREATED.value(), toJson(response));
        eventPublisher.publishEvent(new TransferCompletedEvent(UUID.randomUUID(), transactionId,
                from.getUserId(), from.getEmail(), to.getUserId(), to.getEmail(), amount, from.getCurrency(), now));

        return new TransferResult(response, false);
    }

    private TransferResult replay(IdempotencyRecord record, String requestHash) {
        if (!record.matches(requestHash)) {
            throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different request");
        }
        if (record.getResponseBody() == null) {
            throw inProgress();
        }
        try {
            return new TransferResult(objectMapper.readValue(record.getResponseBody(), TransferResponse.class), true);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response is unreadable", e);
        }
    }

    private IdempotencyRecord claimKey(Long userId, String key, String requestHash) {
        try {
            return idempotencyRepository.saveAndFlush(new IdempotencyRecord(userId, key, requestHash));
        } catch (DataIntegrityViolationException e) {
            throw inProgress();
        }
    }

    private static BusinessException inProgress() {
        return new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_IN_PROGRESS",
                "A request with this Idempotency-Key is already being processed; retry shortly");
    }

    private static String validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header is required");
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters");
        }
        return trimmed;
    }

    /** SHA-256 of the meaningful request fields, so "500" and "500.00" count as the same request. */
    static String fingerprint(String toEmail, BigDecimal amount, String note) {
        String canonical = toEmail + "|" + amount.stripTrailingZeros().toPlainString() + "|" + (note == null ? "" : note);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String toJson(TransferResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize transfer response", e);
        }
    }
}
