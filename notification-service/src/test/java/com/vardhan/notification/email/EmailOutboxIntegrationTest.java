package com.vardhan.notification.email;

import com.vardhan.common.events.TopUpCompletedEvent;
import com.vardhan.common.events.TransferCompletedEvent;
import com.vardhan.notification.IntegrationTestBase;
import com.vardhan.notification.notification.TopUpCompletedListener;
import com.vardhan.notification.notification.TransferCompletedListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailOutboxIntegrationTest extends IntegrationTestBase {

    @Autowired
    TransferCompletedListener transferListener;
    @Autowired
    TopUpCompletedListener topUpListener;
    @Autowired
    EmailDispatcher dispatcher;
    @Autowired
    EmailOutboxRepository outbox;
    @Autowired
    EmailOutboxService outboxService;
    @Autowired
    TransactionTemplate transactions;

    private TransferCompletedEvent transfer(String from, String to) {
        return new TransferCompletedEvent(UUID.randomUUID(), UUID.randomUUID(), 1L, from, 2L, to,
                new BigDecimal("320.00"), "INR", Instant.now());
    }

    /** Pretend the retry wait has passed. */
    private void makeEverythingDue() {
        jdbc.update("UPDATE email_outbox SET next_attempt_at = now() - interval '1 second' WHERE status IN ('PENDING', 'SENDING')");
    }

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void transfer_emailsBothPeopleOnce_evenWhenTheEventIsDeliveredTwice() {
        TransferCompletedEvent event = transfer("vardhan@test.com", "friend@test.com");

        transferListener.onTransferCompleted(event);
        transferListener.onTransferCompleted(event);   // RabbitMQ redelivery

        assertThat(count("SELECT count(*) FROM notifications")).isEqualTo(2);
        assertThat(outbox.findByEventIdOrderByIdAsc(event.eventId()))
                .extracting(EmailOutbox::getRecipient, EmailOutbox::getStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("vardhan@test.com", EmailStatus.PENDING),
                        org.assertj.core.groups.Tuple.tuple("friend@test.com", EmailStatus.PENDING));

        assertThat(dispatcher.dispatchDue()).isEqualTo(2);
        assertThat(dispatcher.dispatchDue()).isZero();

        assertThat(emailSender.accepted()).extracting(EmailMessage::to, EmailMessage::subject).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("vardhan@test.com", "You sent ₹320.00 to friend@test.com"),
                org.assertj.core.groups.Tuple.tuple("friend@test.com", "You received ₹320.00 from vardhan@test.com"));
        assertThat(outbox.findByEventIdOrderByIdAsc(event.eventId())).allSatisfy(email -> {
            assertThat(email.getStatus()).isEqualTo(EmailStatus.SENT);
            assertThat(email.getAttempts()).isEqualTo(1);
            assertThat(email.getSentAt()).isNotNull();
            assertThat(email.getProviderMessageId()).startsWith("test-");
        });
    }

    @Test
    void topUp_emailsTheWalletOwner() {
        TopUpCompletedEvent event = new TopUpCompletedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 7L,
                "vardhan@test.com", new BigDecimal("2000.00"), "INR", "MOCK", Instant.now());

        topUpListener.onTopUpCompleted(event);
        dispatcher.dispatchDue();

        assertThat(emailSender.accepted()).singleElement().satisfies(email -> {
            assertThat(email.to()).isEqualTo("vardhan@test.com");
            assertThat(email.subject()).isEqualTo("₹2,000.00 added to your Smart Wallet");
            assertThat(email.textBody()).contains("Paid through: Demo Bank");
        });
    }

    @Test
    void demoAccounts_getTheInAppNotificationButNoEmail() {
        TransferCompletedEvent event = transfer("vardhan@test.com", "guest-1c5d5f38@demo.smartwallet.dev");

        transferListener.onTransferCompleted(event);

        assertThat(count("SELECT count(*) FROM notifications")).isEqualTo(2);
        assertThat(outbox.findByEventIdOrderByIdAsc(event.eventId())).extracting(EmailOutbox::getRecipient)
                .containsExactly("vardhan@test.com");
    }

    @Test
    void theDatabaseItselfRefusesASecondEmailForTheSamePersonAndEvent() {
        UUID eventId = UUID.randomUUID();
        EmailMessage message = new EmailMessage("a@test.com", "s", "t", "h");
        transactions.executeWithoutResult(s -> outboxService.enqueue(eventId, 1L, message));

        Boolean queuedAgain = transactions.execute(s -> outboxService.enqueue(eventId, 1L, message));
        assertThat(queuedAgain).isFalse();
        assertThatThrownBy(() -> outbox.saveAndFlush(new EmailOutbox(eventId, 1L, message)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void providerHiccup_isRetriedLaterWithBackoff_thenSent() {
        transferListener.onTransferCompleted(transfer("vardhan@test.com", "guest@demo.smartwallet.dev"));
        emailSender.failNext(new EmailSendException("Brevo answered 503", true));

        Instant before = Instant.now();
        assertThat(dispatcher.dispatchDue()).isZero();

        EmailOutbox waiting = outbox.findAll().get(0);
        assertThat(waiting.getStatus()).isEqualTo(EmailStatus.PENDING);
        assertThat(waiting.getAttempts()).isEqualTo(1);
        assertThat(waiting.getLastError()).isEqualTo("Brevo answered 503");
        assertThat(waiting.getNextAttemptAt()).isBetween(before.plusSeconds(29), before.plusSeconds(35));

        assertThat(dispatcher.dispatchDue()).as("not due yet").isZero();
        assertThat(emailSender.calls()).isEqualTo(1);

        makeEverythingDue();
        assertThat(dispatcher.dispatchDue()).isEqualTo(1);

        EmailOutbox sent = outbox.findAll().get(0);
        assertThat(sent.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(sent.getAttempts()).isEqualTo(2);
        assertThat(sent.getLastError()).isNull();
    }

    @Test
    void rejectedEmail_failsAtOnce_andIsNeverSentAgain() {
        transferListener.onTransferCompleted(transfer("vardhan@test.com", "guest@demo.smartwallet.dev"));
        emailSender.failNext(new EmailSendException("Brevo answered 400: email is not valid", false));

        dispatcher.dispatchDue();
        makeEverythingDue();
        dispatcher.dispatchDue();

        EmailOutbox failed = outbox.findAll().get(0);
        assertThat(failed.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(emailSender.calls()).isEqualTo(1);
    }

    @Test
    void providerDownForLong_givesUpAfterSixAttempts() {
        transferListener.onTransferCompleted(transfer("vardhan@test.com", "guest@demo.smartwallet.dev"));
        emailSender.failAlways(new EmailSendException("Could not reach Brevo", true));

        for (int i = 0; i < 10; i++) {
            makeEverythingDue();
            dispatcher.dispatchDue();
        }

        EmailOutbox failed = outbox.findAll().get(0);
        assertThat(failed.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(6);
        assertThat(emailSender.calls()).isEqualTo(6);
    }

    @Test
    void emailClaimedByAnInstanceThatCrashed_isPickedUpWhenItsLeaseRunsOut() {
        transferListener.onTransferCompleted(transfer("vardhan@test.com", "guest@demo.smartwallet.dev"));
        // what a crash between claiming and recording leaves behind: SENDING, one attempt used
        jdbc.update("UPDATE email_outbox SET status = 'SENDING', attempts = 1, next_attempt_at = now() + interval '2 minutes'");

        assertThat(dispatcher.dispatchDue()).as("lease still running").isZero();

        makeEverythingDue();
        assertThat(dispatcher.dispatchDue()).isEqualTo(1);
        assertThat(outbox.findAll().get(0).getAttempts()).isEqualTo(2);
    }

    @Test
    void crashOnTheLastAttempt_endsAsFailedInsteadOfStuck() {
        transferListener.onTransferCompleted(transfer("vardhan@test.com", "guest@demo.smartwallet.dev"));
        jdbc.update("UPDATE email_outbox SET status = 'SENDING', attempts = 6, next_attempt_at = now() - interval '1 second'");

        dispatcher.dispatchDue();

        assertThat(outbox.findAll().get(0).getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(emailSender.calls()).isZero();
    }

    @Test
    void fourDispatchersAtOnce_sendEveryEmailExactlyOnce() throws Exception {
        for (int i = 0; i < 30; i++) {
            transferListener.onTransferCompleted(transfer("sender" + i + "@test.com", "receiver" + i + "@test.com"));
        }
        emailSender.delay(15);   // keep claims overlapping

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> runs = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                runs.add(() -> {
                    int sent = 0;
                    for (int round = 0; round < 5; round++) {
                        sent += dispatcher.dispatchDue();
                    }
                    return sent;
                });
            }
            int total = 0;
            for (Future<Integer> run : pool.invokeAll(runs)) {
                total += run.get();
            }
            assertThat(total).isEqualTo(60);
        } finally {
            pool.shutdownNow();
        }

        Map<String, Long> perRecipient = emailSender.accepted().stream()
                .collect(Collectors.groupingBy(EmailMessage::to, Collectors.counting()));
        assertThat(perRecipient).hasSize(60).allSatisfy((to, n) -> assertThat(n).as(to).isEqualTo(1L));
        assertThat(emailSender.calls()).isEqualTo(60);
        assertThat(count("SELECT count(*) FROM email_outbox WHERE status = 'SENT'")).isEqualTo(60);
    }

    @Test
    void anEmailThatFailsDoesNotHoldUpTheOthers() {
        transferListener.onTransferCompleted(transfer("first@test.com", "guest@demo.smartwallet.dev"));
        transferListener.onTransferCompleted(transfer("second@test.com", "guest@demo.smartwallet.dev"));
        emailSender.failNext(new EmailSendException("Brevo answered 500", true));

        assertThat(dispatcher.dispatchDue()).isEqualTo(1);

        Map<EmailStatus, Long> byStatus = outbox.findAll().stream()
                .collect(Collectors.groupingBy(EmailOutbox::getStatus, Collectors.counting()));
        assertThat(byStatus).containsEntry(EmailStatus.SENT, 1L).containsEntry(EmailStatus.PENDING, 1L);
    }
}
