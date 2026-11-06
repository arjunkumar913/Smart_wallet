package com.vardhan.wallet.wallet;

import com.vardhan.common.web.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true)
    private Long userId;

    @Column(unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_type", nullable = false)
    private WalletType walletType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
        // for JPA
    }

    public static Wallet forUser(Long userId, String email) {
        Wallet wallet = new Wallet();
        wallet.userId = userId;
        wallet.email = email;
        wallet.walletType = WalletType.USER;
        wallet.balance = BigDecimal.ZERO.setScale(2);
        wallet.currency = "INR";
        wallet.status = WalletStatus.ACTIVE;
        return wallet;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Callers must hold a pessimistic lock on this row. */
    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    /** Callers must hold a pessimistic lock on this row. */
    public void debit(BigDecimal amount) {
        if (walletType == WalletType.USER && balance.compareTo(amount) < 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS",
                    "Wallet balance is too low for this operation");
        }
        this.balance = this.balance.subtract(amount);
    }

    public void ensureActive() {
        if (status != WalletStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "WALLET_FROZEN", "Wallet is frozen");
        }
    }

    public boolean isActive() {
        return status == WalletStatus.ACTIVE;
    }

    public void freeze() {
        this.status = WalletStatus.FROZEN;
    }

    public void unfreeze() {
        this.status = WalletStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public WalletType getWalletType() {
        return walletType;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
