package com.vardhan.wallet.wallet;

import com.vardhan.common.web.BusinessException;
import com.vardhan.wallet.ledger.EntryType;
import com.vardhan.wallet.ledger.LedgerEntry;
import com.vardhan.wallet.ledger.LedgerEntryRepository;
import com.vardhan.wallet.web.TopUpResponse;
import com.vardhan.wallet.web.WalletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletLocker walletLocker;
    private final BigDecimal maxTopUp;

    public WalletService(WalletRepository walletRepository, LedgerEntryRepository ledgerEntryRepository,
                         WalletLocker walletLocker, @Value("${app.wallet.max-top-up:50000.00}") BigDecimal maxTopUp) {
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.walletLocker = walletLocker;
        this.maxTopUp = maxTopUp;
    }

    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .map(WalletResponse::from)
                .orElseThrow(WalletService::walletNotFound);
    }

    /**
     * Simulated top-up, recorded as a real double-entry movement:
     * DEBIT the SYSTEM funding wallet, CREDIT the user's wallet, both in one ACID transaction.
     */
    @Transactional
    public TopUpResponse topUp(Long userId, BigDecimal amount) {
        if (amount.compareTo(maxTopUp) > 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TOP_UP_LIMIT_EXCEEDED",
                    "A single top-up cannot exceed " + maxTopUp);
        }
        Long userWalletId = walletRepository.findIdByUserId(userId).orElseThrow(WalletService::walletNotFound);
        Long systemWalletId = walletRepository.findIdByWalletType(WalletType.SYSTEM)
                .orElseThrow(() -> new IllegalStateException("SYSTEM wallet is missing; check Liquibase seed"));

        Map<Long, Wallet> locked = walletLocker.lockInOrder(userWalletId, systemWalletId);
        Wallet userWallet = locked.get(userWalletId);
        Wallet systemWallet = locked.get(systemWalletId);

        userWallet.ensureActive();
        systemWallet.debit(amount);
        userWallet.credit(amount);

        UUID transactionId = UUID.randomUUID();
        ledgerEntryRepository.save(new LedgerEntry(transactionId, systemWallet.getId(), EntryType.DEBIT,
                amount, systemWallet.getBalance(), "Top-up funding for wallet " + userWallet.getId()));
        ledgerEntryRepository.save(new LedgerEntry(transactionId, userWallet.getId(), EntryType.CREDIT,
                amount, userWallet.getBalance(), "Wallet top-up"));

        return new TopUpResponse(transactionId, userWallet.getId(), amount, userWallet.getBalance(), Instant.now());
    }

    private static BusinessException walletNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND",
                "Wallet not found (if you just registered, it is created within a few seconds)");
    }
}
