package com.vardhan.wallet.topup;

import com.vardhan.common.events.TopUpCompletedEvent;
import com.vardhan.wallet.payment.GatewayPayment;
import com.vardhan.wallet.payment.RazorpayGateway;
import com.vardhan.wallet.wallet.WalletService;
import com.vardhan.wallet.wallet.WalletService.Funding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The only place a paid top-up turns into wallet money.
 *
 * The same payment can be reported up to three times: by the gateway's webhook (possibly retried),
 * by the browser's signed confirmation, and by the reconciliation job. Every path calls
 * {@link #settleSuccess}, which locks the top-up row first, so the reports take turns and only the
 * first one credits the wallet. The rest see SUCCESS and do nothing: exactly-once crediting.
 */
@Service
public class TopUpSettlementService {

    private static final Logger log = LoggerFactory.getLogger(TopUpSettlementService.class);

    private final TopUpRepository topUpRepository;
    private final WalletService walletService;
    private final ApplicationEventPublisher eventPublisher;

    public TopUpSettlementService(TopUpRepository topUpRepository, WalletService walletService,
                                  ApplicationEventPublisher eventPublisher) {
        this.topUpRepository = topUpRepository;
        this.walletService = walletService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * One database transaction: lock the top-up, credit the wallet (ledger pair), mark SUCCESS.
     * Any failure rolls all of it back, and the next report of the same payment tries again.
     *
     * @param paidMinorUnits the amount the gateway says was paid, in paise, when it told us; null otherwise
     * @return true if this call credited the wallet, false if there was nothing to do
     */
    @Transactional
    public boolean settleSuccess(String providerOrderId, GatewayPayment payment, Long paidMinorUnits) {
        Optional<UUID> topUpId = topUpRepository.findIdByProviderOrderId(providerOrderId);
        if (topUpId.isEmpty()) {
            log.warn("Payment {} is for unknown order {}; ignored", payment.paymentId(), providerOrderId);
            return false;
        }
        TopUp topUp = topUpRepository.findByIdForUpdate(topUpId.get()).orElseThrow();
        if (topUp.isSuccess()) {
            log.info("Top-up {} already credited; duplicate report of payment {} ignored", topUp.getId(), payment.paymentId());
            return false;
        }
        long expectedMinorUnits = RazorpayGateway.toMinorUnits(topUp.getAmount());
        if (paidMinorUnits != null && paidMinorUnits != expectedMinorUnits) {
            // Never credit an amount the gateway did not confirm. Left for a human to look at.
            log.error("Top-up {} expects {} paise but payment {} is for {} paise; NOT credited",
                    topUp.getId(), expectedMinorUnits, payment.paymentId(), paidMinorUnits);
            return false;
        }

        // The money has been collected, so the wallet is credited even if it was frozen in the meantime.
        Funding funding = walletService.fundFromSystem(topUp.getWalletId(), topUp.getAmount(),
                describe(topUp.getProvider(), payment.method()), false);
        topUp.succeed(payment.paymentId(), payment.method(), funding.transactionId());

        eventPublisher.publishEvent(new TopUpCompletedEvent(UUID.randomUUID(), topUp.getId(), funding.transactionId(),
                funding.userId(), funding.email(), topUp.getAmount(), topUp.getCurrency(), topUp.getProvider(), Instant.now()));
        log.info("Top-up {} credited: payment={} transactionId={}", topUp.getId(), payment.paymentId(), funding.transactionId());
        return true;
    }

    /** A failed attempt only matters while we are still waiting; it never undoes a success. */
    @Transactional
    public void markFailed(String providerOrderId, String reason) {
        topUpRepository.findIdByProviderOrderId(providerOrderId)
                .flatMap(topUpRepository::findByIdForUpdate)
                .filter(TopUp::isAwaitingPayment)
                .ifPresent(topUp -> {
                    topUp.fail(reason);
                    log.info("Top-up {} failed: {}", topUp.getId(), reason);
                });
    }

    @Transactional
    public void expire(UUID topUpId) {
        topUpRepository.findByIdForUpdate(topUpId)
                .filter(topUp -> topUp.getStatus() == TopUpStatus.PENDING || topUp.getStatus() == TopUpStatus.CREATED)
                .ifPresent(topUp -> {
                    topUp.expire();
                    log.info("Top-up {} expired without a payment", topUp.getId());
                });
    }

    /** What the user sees in their transaction history, e.g. "Added money via Razorpay (UPI)". */
    static String describe(String provider, String method) {
        String via = "MOCK".equals(provider) ? "Demo Bank"
                : provider.charAt(0) + provider.substring(1).toLowerCase(Locale.ROOT);
        if (method == null || method.isBlank()) {
            return "Added money via " + via;
        }
        String shownMethod = method.length() <= 3 ? method.toUpperCase(Locale.ROOT) : method;
        return "Added money via " + via + " (" + shownMethod + ")";
    }
}
