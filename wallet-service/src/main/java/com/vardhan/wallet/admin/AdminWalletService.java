package com.vardhan.wallet.admin;

import com.vardhan.common.web.BusinessException;
import com.vardhan.common.web.PageResponse;
import com.vardhan.wallet.wallet.Wallet;
import com.vardhan.wallet.wallet.WalletRepository;
import com.vardhan.wallet.wallet.WalletType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminWalletService {

    private static final Logger log = LoggerFactory.getLogger(AdminWalletService.class);

    private final WalletRepository walletRepository;

    public AdminWalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminWalletResponse> list(int page, int size) {
        return PageResponse.of(walletRepository.findAll(PageRequest.of(page, size, Sort.by("id"))), AdminWalletResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminWalletResponse get(Long walletId) {
        return walletRepository.findById(walletId).map(AdminWalletResponse::from).orElseThrow(AdminWalletService::notFound);
    }

    /**
     * Takes the same row lock as transfers, so a freeze waits for any in-flight transfer on this
     * wallet to finish, and every transfer that starts afterwards sees the wallet as FROZEN.
     * Idempotent: freezing a frozen wallet is a no-op.
     */
    @Transactional
    public AdminWalletResponse freeze(Long walletId, String adminEmail) {
        Wallet wallet = lockUserWallet(walletId);
        wallet.freeze();
        log.info("Wallet {} frozen by {}", walletId, adminEmail);
        return AdminWalletResponse.from(wallet);
    }

    @Transactional
    public AdminWalletResponse unfreeze(Long walletId, String adminEmail) {
        Wallet wallet = lockUserWallet(walletId);
        wallet.unfreeze();
        log.info("Wallet {} unfrozen by {}", walletId, adminEmail);
        return AdminWalletResponse.from(wallet);
    }

    private Wallet lockUserWallet(Long walletId) {
        Wallet wallet = walletRepository.findByIdForUpdate(walletId).orElseThrow(AdminWalletService::notFound);
        if (wallet.getWalletType() == WalletType.SYSTEM) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SYSTEM_WALLET_PROTECTED",
                    "The SYSTEM funding wallet cannot be frozen or unfrozen");
        }
        return wallet;
    }

    private static BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found");
    }
}
