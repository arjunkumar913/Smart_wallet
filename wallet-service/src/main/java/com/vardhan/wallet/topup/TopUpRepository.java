package com.vardhan.wallet.topup;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TopUpRepository extends JpaRepository<TopUp, UUID> {

    Optional<TopUp> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /** Looking up by id AND owner means another user's top-up is simply "not found". */
    Optional<TopUp> findByIdAndUserId(UUID id, Long userId);

    /*
     * Settlement resolves ids without loading the entity, then loads the row ONCE through the locking
     * query below (same reason as in WalletRepository: an entity loaded unlocked first would stay
     * stale in the persistence context even after the lock is taken).
     */
    @Query("select t.id from TopUp t where t.providerOrderId = :orderId")
    Optional<UUID> findIdByProviderOrderId(@Param("orderId") String orderId);

    /**
     * SELECT ... FOR UPDATE: a webhook, the browser's confirmation and the reconciliation job can all
     * report the same payment at the same moment; this lock makes them take turns.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TopUp t where t.id = :id")
    Optional<TopUp> findByIdForUpdate(@Param("id") UUID id);

    @Query("select t.id from TopUp t where t.status = :status and t.createdAt < :before order by t.createdAt")
    List<UUID> findIdsByStatusCreatedBefore(@Param("status") TopUpStatus status, @Param("before") Instant before,
                                            Pageable pageable);
}
