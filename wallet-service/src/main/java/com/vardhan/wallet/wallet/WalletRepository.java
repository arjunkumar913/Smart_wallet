package com.vardhan.wallet.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    boolean existsByUserId(Long userId);

    Optional<Wallet> findByUserId(Long userId);

    /*
     * The two id lookups below deliberately return only ids, not entities.
     * Money-moving code first resolves ids, then loads each wallet ONCE through
     * findByIdForUpdate. If an entity were loaded unlocked first, the persistence
     * context would keep that (possibly stale) copy even after the row lock is taken.
     */

    @Query("select w.id from Wallet w where w.userId = :userId")
    Optional<Long> findIdByUserId(@Param("userId") Long userId);

    @Query("select w.id from Wallet w where w.email = :email")
    Optional<Long> findIdByEmail(@Param("email") String email);

    @Query("select w.id from Wallet w where w.walletType = :type")
    Optional<Long> findIdByWalletType(@Param("type") WalletType type);

    /** SELECT ... FOR UPDATE: blocks other transactions touching this wallet until we commit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") Long id);
}
