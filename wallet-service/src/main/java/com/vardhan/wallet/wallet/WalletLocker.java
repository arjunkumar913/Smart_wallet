package com.vardhan.wallet.wallet;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Locks wallets in ascending id order.
 *
 * Why the order matters: if transfer A->B locks A then B while transfer B->A locks B then A,
 * each waits for the other forever (deadlock). When every transaction locks the lower id
 * first, that cycle cannot form.
 */
@Component
public class WalletLocker {

    private final WalletRepository walletRepository;

    public WalletLocker(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, Wallet> lockInOrder(Long firstId, Long secondId) {
        Map<Long, Wallet> locked = new HashMap<>();
        Stream.of(firstId, secondId).distinct().sorted().forEach(id ->
                locked.put(id, walletRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new IllegalStateException("Wallet " + id + " disappeared while locking"))));
        return locked;
    }
}
