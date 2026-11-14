package com.vardhan.wallet.topup;

import com.vardhan.wallet.payment.GatewayPayment;
import com.vardhan.wallet.payment.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The safety net for lost messages. Webhooks can fail to arrive and browsers get closed, so on a timer
 * we ask the gateway directly about every top-up still waiting for payment:
 * paid -> credit it (through the same settlement code as everything else); too old -> expire it.
 */
@Component
public class TopUpReconciler {

    private static final Logger log = LoggerFactory.getLogger(TopUpReconciler.class);
    private static final int BATCH_SIZE = 50;

    private final TopUpRepository topUpRepository;
    private final PaymentGateway gateway;
    private final TopUpSettlementService settlement;
    private final Duration grace;
    private final Duration timeout;

    public TopUpReconciler(TopUpRepository topUpRepository, PaymentGateway gateway, TopUpSettlementService settlement,
                           @Value("${app.payments.reconcile-after-seconds:120}") long graceSeconds,
                           @Value("${app.payments.pending-timeout-minutes:30}") long timeoutMinutes) {
        this.topUpRepository = topUpRepository;
        this.gateway = gateway;
        this.settlement = settlement;
        this.grace = Duration.ofSeconds(graceSeconds);
        this.timeout = Duration.ofMinutes(timeoutMinutes);
    }

    @Scheduled(fixedDelayString = "${app.payments.reconcile-interval-ms:60000}",
            initialDelayString = "${app.payments.reconcile-interval-ms:60000}")
    public void reconcile() {
        Instant now = Instant.now();
        // Give the normal paths (webhook, browser confirmation) a moment before asking the gateway.
        topUpRepository.findIdsByStatusCreatedBefore(TopUpStatus.PENDING, now.minus(grace), PageRequest.of(0, BATCH_SIZE))
                .forEach(this::reconcileQuietly);
        // Rows whose gateway order was never created (the service stopped mid-request).
        topUpRepository.findIdsByStatusCreatedBefore(TopUpStatus.CREATED, now.minus(timeout), PageRequest.of(0, BATCH_SIZE))
                .forEach(settlement::expire);
    }

    /** One top-up, checked against the gateway right now. Safe to call at any time and any number of times. */
    public void reconcileOne(UUID topUpId) {
        TopUp topUp = topUpRepository.findById(topUpId).orElse(null);
        if (topUp == null || !topUp.isAwaitingPayment()) {
            return;
        }
        Optional<GatewayPayment> payment = gateway.findSuccessfulPayment(topUp.getProviderOrderId());
        if (payment.isPresent()) {
            settlement.settleSuccess(topUp.getProviderOrderId(), payment.get(), null);
        } else if (topUp.getCreatedAt().isBefore(Instant.now().minus(timeout))) {
            settlement.expire(topUp.getId());
        }
    }

    private void reconcileQuietly(UUID topUpId) {
        try {
            reconcileOne(topUpId);
        } catch (RuntimeException e) {
            // one unreachable order must not stop the rest of the batch; it is retried on the next run
            log.warn("Could not reconcile top-up {}: {}", topUpId, e.getMessage());
        }
    }
}
