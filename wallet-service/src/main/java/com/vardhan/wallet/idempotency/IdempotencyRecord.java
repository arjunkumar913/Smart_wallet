package com.vardhan.wallet.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per (user, Idempotency-Key). Stores a fingerprint of the request and the response
 * that was returned, so a retry with the same key gets the original answer instead of moving money twice.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // for JPA
    }

    public IdempotencyRecord(Long userId, String idempotencyKey, String requestHash) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void complete(int status, String body) {
        this.responseStatus = status;
        this.responseBody = body;
    }

    public boolean matches(String otherRequestHash) {
        return requestHash.equals(otherRequestHash);
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }
}
