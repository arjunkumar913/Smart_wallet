package com.vardhan.notification.notification;

import com.vardhan.common.events.TransferCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferCompletedListenerTest {

    private NotificationRepository repository;
    private TransferCompletedListener listener;

    private final TransferCompletedEvent event = new TransferCompletedEvent(UUID.randomUUID(), UUID.randomUUID(),
            1L, "vardhan@test.com", 2L, "friend@test.com", new BigDecimal("250.00"), "INR", Instant.now());

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        listener = new TransferCompletedListener(repository);
    }

    @Test
    void newEvent_notifiesSenderAndRecipient() {
        listener.onTransferCompleted(event);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getUserId).containsExactly(1L, 2L);
        assertThat(saved.getAllValues()).extracting(Notification::getMessage).containsExactly(
                "You sent ₹250.00 to friend@test.com",
                "You received ₹250.00 from vardhan@test.com");
    }

    @Test
    void redeliveredEvent_createsNothing() {
        when(repository.existsByEventIdAndUserId(eq(event.eventId()), any())).thenReturn(true);

        listener.onTransferCompleted(event);

        verify(repository, never()).save(any());
    }

    @Test
    void partiallyProcessedEvent_onlyCreatesTheMissingNotification() {
        when(repository.existsByEventIdAndUserId(event.eventId(), 1L)).thenReturn(true);

        listener.onTransferCompleted(event);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(2L);
    }

    @Test
    void nonInrAmounts_showTheCurrencyCode() {
        assertThat(TransferCompletedListener.formatAmount(new BigDecimal("10.5"), "USD")).isEqualTo("USD 10.50");
    }
}
