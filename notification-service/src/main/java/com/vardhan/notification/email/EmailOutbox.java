package com.vardhan.notification.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_outbox")
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false, length = 320)
    private String recipient;

    @Column(nullable = false, updatable = false, length = 300)
    private String subject;

    @Column(name = "text_body", nullable = false, updatable = false)
    private String textBody;

    @Column(name = "html_body", nullable = false, updatable = false)
    private String htmlBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmailStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected EmailOutbox() {
        // for JPA
    }

    public EmailOutbox(UUID eventId, Long userId, EmailMessage message) {
        this.eventId = eventId;
        this.userId = userId;
        this.recipient = message.to();
        this.subject = message.subject();
        this.textBody = message.textBody();
        this.htmlBody = message.htmlBody();
        this.status = EmailStatus.PENDING;
        this.nextAttemptAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public EmailMessage toMessage() {
        return new EmailMessage(recipient, subject, textBody, htmlBody);
    }

    void markSent(String providerMessageId, Instant now) {
        this.status = EmailStatus.SENT;
        this.providerMessageId = truncate(providerMessageId, 200);
        this.sentAt = now;
        this.lastError = null;
    }

    void retryAt(Instant when, String error) {
        this.status = EmailStatus.PENDING;
        this.nextAttemptAt = when;
        this.lastError = truncate(error, 500);
    }

    void fail(String error) {
        this.status = EmailStatus.FAILED;
        this.lastError = truncate(error, 500);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public Long getUserId() { return userId; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public EmailStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public String getProviderMessageId() { return providerMessageId; }
    public Instant getSentAt() { return sentAt; }
}
