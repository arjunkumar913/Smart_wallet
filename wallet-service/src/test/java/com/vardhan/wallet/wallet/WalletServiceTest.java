package com.vardhan.wallet.wallet;

import com.vardhan.common.web.BusinessException;
import com.vardhan.wallet.ledger.EntryType;
import com.vardhan.wallet.ledger.LedgerEntry;
import com.vardhan.wallet.ledger.LedgerEntryRepository;
import com.vardhan.wallet.web.TopUpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletServiceTest {

    private static final long USER_ID = 7L;
    private static final long USER_WALLET_ID = 2L;
    private static final long SYSTEM_WALLET_ID = 1L;

    private WalletRepository walletRepository;
    private LedgerEntryRepository ledgerEntryRepository;
    private WalletLocker walletLocker;
    private WalletService walletService;

    private Wallet userWallet;
    private Wallet systemWallet;

    @BeforeEach
    void setUp() {
        walletRepository = mock(WalletRepository.class);
        ledgerEntryRepository = mock(LedgerEntryRepository.class);
        walletLocker = mock(WalletLocker.class);
        walletService = new WalletService(walletRepository, ledgerEntryRepository, walletLocker, new BigDecimal("50000.00"));

        userWallet = Wallet.forUser(USER_ID, "vardhan@example.com");
        ReflectionTestUtils.setField(userWallet, "id", USER_WALLET_ID);
        systemWallet = Wallet.forUser(0L, "system@internal");
        ReflectionTestUtils.setField(systemWallet, "id", SYSTEM_WALLET_ID);
        ReflectionTestUtils.setField(systemWallet, "walletType", WalletType.SYSTEM);

        when(walletRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(USER_WALLET_ID));
        when(walletRepository.findIdByWalletType(WalletType.SYSTEM)).thenReturn(Optional.of(SYSTEM_WALLET_ID));
        when(walletLocker.lockInOrder(USER_WALLET_ID, SYSTEM_WALLET_ID))
                .thenReturn(Map.of(USER_WALLET_ID, userWallet, SYSTEM_WALLET_ID, systemWallet));
    }

    @Test
    void topUp_movesMoneyFromSystemWallet_andWritesBalancedLedgerPair() {
        TopUpResponse response = walletService.topUp(USER_ID, new BigDecimal("500.00"));

        assertThat(userWallet.getBalance()).isEqualByComparingTo("500.00");
        assertThat(systemWallet.getBalance()).isEqualByComparingTo("-500.00");
        assertThat(response.balanceAfter()).isEqualByComparingTo("500.00");

        ArgumentCaptor<LedgerEntry> entries = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(entries.capture());
        List<LedgerEntry> saved = entries.getAllValues();
        assertThat(saved).extracting(LedgerEntry::getEntryType).containsExactly(EntryType.DEBIT, EntryType.CREDIT);
        assertThat(saved).extracting(LedgerEntry::getTransactionId).containsOnly(response.transactionId());
        assertThat(saved).extracting(LedgerEntry::getWalletId).containsExactly(SYSTEM_WALLET_ID, USER_WALLET_ID);
    }

    @Test
    void topUp_aboveLimit_isRejected_beforeAnyLocking() {
        assertThatThrownBy(() -> walletService.topUp(USER_ID, new BigDecimal("50000.01")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("TOP_UP_LIMIT_EXCEEDED"));
        verify(walletLocker, never()).lockInOrder(any(), any());
    }

    @Test
    void topUp_onFrozenWallet_isRejected_andNothingIsWritten() {
        userWallet.freeze();

        assertThatThrownBy(() -> walletService.topUp(USER_ID, new BigDecimal("100.00")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("WALLET_FROZEN"));
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void topUp_forUserWithoutWallet_isNotFound() {
        when(walletRepository.findIdByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.topUp(99L, new BigDecimal("100.00")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("WALLET_NOT_FOUND"));
    }

    @Test
    void userWallet_cannotBeDebitedBelowZero() {
        assertThatThrownBy(() -> userWallet.debit(new BigDecimal("0.01")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INSUFFICIENT_FUNDS"));
    }
}
