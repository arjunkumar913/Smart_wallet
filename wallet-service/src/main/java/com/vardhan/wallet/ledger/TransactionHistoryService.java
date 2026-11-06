package com.vardhan.wallet.ledger;

import com.vardhan.common.web.BusinessException;
import com.vardhan.common.web.PageResponse;
import com.vardhan.wallet.wallet.WalletRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class TransactionHistoryService {

    private static final Instant BEGINNING = Instant.EPOCH;
    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TransactionHistoryService(WalletRepository walletRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * The caller's ledger entries (top-ups, sent and received transfers), newest first.
     * {@code from} and {@code to} are inclusive calendar dates in UTC; either may be omitted.
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionHistoryEntry> history(Long userId, LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "'from' must not be after 'to'");
        }
        Long walletId = walletRepository.findIdByUserId(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found"));
        Instant start = from == null ? BEGINNING : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to == null ? FAR_FUTURE : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        return PageResponse.of(ledgerEntryRepository.findHistory(walletId, start, end, PageRequest.of(page, size)),
                TransactionHistoryEntry::from);
    }
}
