package com.vardhan.wallet.topup;

import com.vardhan.common.web.BusinessException;
import com.vardhan.wallet.idempotency.IdempotencyKeys;
import com.vardhan.wallet.payment.GatewayOrder;
import com.vardhan.wallet.payment.GatewayPayment;
import com.vardhan.wallet.payment.PaymentGateway;
import com.vardhan.wallet.payment.PaymentGatewayException;
import com.vardhan.wallet.wallet.Wallet;
import com.vardhan.wallet.wallet.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Starts top-ups and answers questions about them. It never credits a wallet itself:
 * that only happens in {@link TopUpSettlementService}, once the gateway has confirmed the payment.
 */
@Service
public class TopUpService {

    private static final Logger log = LoggerFactory.getLogger(TopUpService.class);

    private final TopUpRepository topUpRepository;
    private final WalletRepository walletRepository;
    private final PaymentGateway gateway;
    private final TopUpSettlementService settlement;
    private final TopUpReconciler reconciler;
    private final BigDecimal maxTopUp;

    public TopUpService(TopUpRepository topUpRepository, WalletRepository walletRepository, PaymentGateway gateway,
                        TopUpSettlementService settlement, TopUpReconciler reconciler,
                        @Value("${app.wallet.max-top-up:50000.00}") BigDecimal maxTopUp) {
        this.topUpRepository = topUpRepository;
        this.walletRepository = walletRepository;
        this.gateway = gateway;
        this.settlement = settlement;
        this.reconciler = reconciler;
        this.maxTopUp = maxTopUp;
    }

    /**
     * Deliberately NOT one database transaction: it calls the gateway over HTTP in the middle, and a
     * slow gateway must not hold a database connection or row locks. Instead:
     * 1. claim the Idempotency-Key by inserting the top-up (committed immediately),
     * 2. create the gateway order,
     * 3. attach the order id and start waiting for the payment.
     * No money moves here, so a crash between the steps loses nothing; the reconciler expires leftovers.
     */
    public TopUpResult create(Long userId, String idempotencyKey, BigDecimal amount) {
        String key = IdempotencyKeys.validate(idempotencyKey);
        if (amount.compareTo(maxTopUp) > 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TOP_UP_LIMIT_EXCEEDED",
                    "A single top-up cannot exceed " + maxTopUp);
        }

        // Seen this key before? Return the same top-up; never create a second gateway order.
        Optional<TopUp> previous = topUpRepository.findByUserIdAndIdempotencyKey(userId, key);
        if (previous.isPresent()) {
            return replay(previous.get(), amount);
        }

        Wallet wallet = walletRepository.findByUserId(userId).orElseThrow(TopUpService::walletNotFound);
        wallet.ensureActive();

        // A concurrent request with the same key loses on the unique index (user_id, idempotency_key).
        TopUp topUp;
        try {
            topUp = topUpRepository.saveAndFlush(TopUp.start(userId, wallet.getId(), amount, wallet.getCurrency(),
                    gateway.provider(), key));
        } catch (DataIntegrityViolationException e) {
            throw IdempotencyKeys.inProgress();
        }

        GatewayOrder order;
        try {
            order = gateway.createOrder(topUp.getId(), topUp.getAmount(), topUp.getCurrency());
        } catch (PaymentGatewayException e) {
            log.warn("Gateway order for top-up {} failed: {}", topUp.getId(), e.getMessage());
            topUpRepository.delete(topUp);    // release the key so the client can retry with it
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_UNAVAILABLE",
                    "The payment provider is not responding. Nothing was charged; please try again.");
        }

        topUp.awaitPayment(order.orderId());
        return new TopUpResult(view(topUpRepository.save(topUp)), false);
    }

    public TopUpView get(Long userId, UUID topUpId) {
        return view(owned(userId, topUpId));
    }

    /**
     * The browser reports "the gateway says I paid" together with the gateway's signature.
     * We trust the signature, never the browser: without the gateway's secret it cannot be forged.
     * Lets a top-up complete even when the webhook is slow or cannot reach us (e.g. on localhost).
     */
    public TopUpView confirm(Long userId, UUID topUpId, ConfirmTopUpRequest request) {
        TopUp topUp = owned(userId, topUpId);
        if (topUp.getProviderOrderId() == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "TOP_UP_NOT_READY", "This top-up has no payment order yet");
        }
        if (!gateway.isValidPaymentSignature(topUp.getProviderOrderId(), request.paymentId(), request.signature())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_SIGNATURE",
                    "The payment could not be verified");
        }
        settlement.settleSuccess(topUp.getProviderOrderId(), new GatewayPayment(request.paymentId(), null), null);
        return view(owned(userId, topUpId));
    }

    /** Asks the gateway right now whether this top-up was paid (the same check the reconciler runs on a timer). */
    public TopUpView sync(Long userId, UUID topUpId) {
        reconciler.reconcileOne(owned(userId, topUpId).getId());
        return view(owned(userId, topUpId));
    }

    private TopUpResult replay(TopUp existing, BigDecimal amount) {
        if (existing.getAmount().compareTo(amount) != 0) {
            throw IdempotencyKeys.reused();
        }
        if (existing.getStatus() == TopUpStatus.CREATED) {
            throw IdempotencyKeys.inProgress();
        }
        return new TopUpResult(view(existing), true);
    }

    private TopUp owned(Long userId, UUID topUpId) {
        return topUpRepository.findByIdAndUserId(topUpId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "TOP_UP_NOT_FOUND", "Top-up not found"));
    }

    private TopUpView view(TopUp topUp) {
        return TopUpView.from(topUp, gateway.checkoutKeyId());
    }

    private static BusinessException walletNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND",
                "Wallet not found (if you just registered, it is created within a few seconds)");
    }
}
