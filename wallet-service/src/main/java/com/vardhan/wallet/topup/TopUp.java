package com.vardhan.wallet.topup;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One attempt to add money to a wallet through a payment gateway.
 * The wallet is credited only when this row moves to SUCCESS, and that happens at most once.
 */
@Entity
@Table(name = "top_ups")
public class TopUp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private Long walletId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopUpStatus status;

    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(name = "provider_order_id")
    private String providerOrderId;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "payment_method")
    private String paymentMethod;

    /** The ledger transaction that credited the wallet; set together with SUCCESS. */
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected TopUp() {
        // for JPA
    }

    public static TopUp start(Long userId, Long walletId, BigDecimal amount, String currency,
                              String provider, String idempotencyKey) {
        TopUp topUp = new TopUp();
        topUp.userId = userId;
        topUp.walletId = walletId;
        topUp.amount = amount.setScale(2);
        topUp.currency = currency;
        topUp.provider = provider;
        topUp.idempotencyKey = idempotencyKey;
        topUp.status = TopUpStatus.CREATED;
        return topUp;
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

    public void awaitPayment(String providerOrderId) {
        this.providerOrderId = providerOrderId;
        this.status = TopUpStatus.PENDING;
    }

    /** Callers must hold a pessimistic lock on this row. */
    public void succeed(String providerPaymentId, String paymentMethod, UUID transactionId) {
        this.status = TopUpStatus.SUCCESS;
        this.providerPaymentId = providerPaymentId;
        this.paymentMethod = paymentMethod;
        this.transactionId = transactionId;
        this.failureReason = null;
        this.completedAt = Instant.now();
    }

    /** Callers must hold a pessimistic lock on this row. */
    public void fail(String reason) {
        this.status = TopUpStatus.FAILED;
        this.failureReason = truncate(reason);
    }

    /** Callers must hold a pessimistic lock on this row. */
    public void expire() {
        this.status = TopUpStatus.EXPIRED;
        this.failureReason = "Payment was not completed in time";
    }

    public boolean isSuccess() {
        return status == TopUpStatus.SUCCESS;
    }

    public boolean isAwaitingPayment() {
        return status == TopUpStatus.PENDING;
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 255 ? text : text.substring(0, 255);
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getWalletId() {
        return walletId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TopUpStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderOrderId() {
        return providerOrderId;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
