package com.vardhan.notification.notification;

import com.vardhan.common.events.TopUpCompletedEvent;
import com.vardhan.notification.email.EmailComposer;
import com.vardhan.notification.email.EmailMessage;
import com.vardhan.notification.email.EmailOutboxService;
import com.vardhan.notification.email.TestEmailProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopUpCompletedListenerTest {

    private NotificationRepository repository;
    private EmailOutboxService emails;
    private TopUpCompletedListener listener;

    private final TopUpCompletedEvent event = new TopUpCompletedEvent(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), 7L, "vardhan@test.com", new BigDecimal("500.00"), "INR", "RAZORPAY", Instant.now());

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        emails = mock(EmailOutboxService.class);
        listener = new TopUpCompletedListener(repository, emails, new EmailComposer(TestEmailProperties.defaults()));
    }

    @Test
    void newEvent_notifiesTheWalletOwner() {
        listener.onTopUpCompleted(event);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(7L);
        assertThat(saved.getValue().getMessage()).isEqualTo("₹500.00 was added to your wallet");

        ArgumentCaptor<EmailMessage> queued = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emails).enqueue(org.mockito.ArgumentMatchers.eq(event.eventId()), org.mockito.ArgumentMatchers.eq(7L), queued.capture());
        assertThat(queued.getValue().to()).isEqualTo("vardhan@test.com");
        assertThat(queued.getValue().subject()).isEqualTo("₹500.00 added to your Smart Wallet");
    }

    @Test
    void redeliveredEvent_createsNothing() {
        when(repository.existsByEventIdAndUserId(event.eventId(), 7L)).thenReturn(true);

        listener.onTopUpCompleted(event);

        verify(repository, never()).save(any());
        verify(emails, never()).enqueue(any(), any(), any());
    }
}
