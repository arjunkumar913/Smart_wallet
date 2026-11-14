package com.vardhan.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Sends queued emails. Each run:
 * <ol>
 *   <li><b>Claim</b> a batch of due emails in a short transaction: {@code FOR UPDATE SKIP LOCKED} lets several
 *       instances run side by side without ever claiming the same row, and each claimed row gets a lease
 *       ({@code SENDING} until {@code next_attempt_at}). The transaction commits before any HTTP call.</li>
 *   <li><b>Send</b> each email with no database transaction open.</li>
 *   <li><b>Record</b> the result: SENT, a retry with exponential backoff, or FAILED.</li>
 * </ol>
 * If an instance dies between claim and record, the lease runs out and another run claims the email again.
 * Delivery is therefore at-least-once: a crash after the provider accepted an email but before it was recorded
 * can send it twice. That is the usual trade-off for email; losing it silently would be worse.
 */
@Component
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    private static final String CLAIM_SQL = """
            UPDATE email_outbox
               SET status = 'SENDING', attempts = attempts + 1, next_attempt_at = ?
             WHERE id IN (SELECT id FROM email_outbox
                           WHERE status IN ('PENDING', 'SENDING')
                             AND next_attempt_at <= ?
                             AND attempts < ?
                           ORDER BY next_attempt_at, id
                           LIMIT ?
                           FOR UPDATE SKIP LOCKED)
            RETURNING id
            """;

    /** A claimed email whose lease ran out with no attempts left: its sender died on the last try. */
    private static final String GIVE_UP_SQL = """
            UPDATE email_outbox
               SET status = 'FAILED', last_error = 'No result recorded after the last attempt'
             WHERE status = 'SENDING' AND next_attempt_at <= ? AND attempts >= ?
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final EmailOutboxRepository repository;
    private final EmailSender sender;
    private final EmailProperties.Dispatch settings;
    private final Clock clock;

    @Autowired
    public EmailDispatcher(JdbcTemplate jdbc, TransactionTemplate transactions, EmailOutboxRepository repository,
                           EmailSender sender, EmailProperties properties) {
        this(jdbc, transactions, repository, sender, properties, Clock.systemUTC());
    }

    EmailDispatcher(JdbcTemplate jdbc, TransactionTemplate transactions, EmailOutboxRepository repository,
                    EmailSender sender, EmailProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.repository = repository;
        this.sender = sender;
        this.settings = properties.dispatch();
        this.clock = clock;
    }

    /** @return how many emails were sent successfully in this run */
    public int dispatchDue() {
        Instant now = clock.instant();
        transactions.executeWithoutResult(status ->
                jdbc.update(GIVE_UP_SQL, Timestamp.from(now), settings.maxAttempts()));

        List<Long> claimed = transactions.execute(status -> jdbc.queryForList(CLAIM_SQL, Long.class,
                Timestamp.from(now.plusSeconds(settings.leaseSeconds())), Timestamp.from(now),
                settings.maxAttempts(), settings.batchSize()));
        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }

        int sent = 0;
        for (Long id : claimed) {
            if (sendOne(id)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean sendOne(Long id) {
        EmailOutbox email = repository.findById(id).orElse(null);
        if (email == null || email.getStatus() != EmailStatus.SENDING) {
            return false;
        }
        try {
            String providerId = sender.send(email.toMessage());
            record(id, row -> row.markSent(providerId, clock.instant()));
            log.info("Email id={} to {} sent (provider id {})", id, email.getRecipient(), providerId);
            return true;
        } catch (EmailSendException e) {
            handleFailure(email, e.getMessage(), e.isRetryable());
        } catch (RuntimeException e) {
            // a bug or an unexpected client error: retry, it may be transient, and the attempt limit still applies
            handleFailure(email, e.getClass().getSimpleName() + ": " + e.getMessage(), true);
        }
        return false;
    }

    private void handleFailure(EmailOutbox email, String error, boolean retryable) {
        int attempts = email.getAttempts();
        if (!retryable || attempts >= settings.maxAttempts()) {
            record(email.getId(), row -> row.fail(error));
            log.warn("Email id={} to {} FAILED after {} attempt(s): {}", email.getId(), email.getRecipient(), attempts, error);
            return;
        }
        Instant retryAt = clock.instant().plus(backoff(attempts));
        record(email.getId(), row -> row.retryAt(retryAt, error));
        log.warn("Email id={} to {} failed (attempt {} of {}), retrying at {}: {}",
                email.getId(), email.getRecipient(), attempts, settings.maxAttempts(), retryAt, error);
    }

    /** first-retry-seconds, then doubling: 30s, 1m, 2m, 4m, 8m with the defaults. */
    Duration backoff(int attemptsSoFar) {
        int doublings = Math.min(Math.max(attemptsSoFar - 1, 0), 16);
        return Duration.ofSeconds(settings.firstRetrySeconds() << doublings);
    }

    private void record(Long id, java.util.function.Consumer<EmailOutbox> change) {
        transactions.executeWithoutResult(status -> repository.findById(id)
                .filter(row -> row.getStatus() == EmailStatus.SENDING)   // only the holder of the claim records
                .ifPresent(change));
    }
}
