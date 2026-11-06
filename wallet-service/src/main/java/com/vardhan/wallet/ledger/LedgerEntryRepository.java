package com.vardhan.wallet.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    /** One wallet's entries in [from, to), newest first. Served by idx_ledger_wallet_created. */
    @Query("""
            select e from LedgerEntry e
            where e.walletId = :walletId and e.createdAt >= :from and e.createdAt < :to
            order by e.createdAt desc, e.id desc""")
    Page<LedgerEntry> findHistory(@Param("walletId") Long walletId, @Param("from") Instant from,
                                  @Param("to") Instant to, Pageable pageable);
}
