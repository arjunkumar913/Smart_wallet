package com.vardhan.wallet.topup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vardhan.common.security.AuthenticatedUser;
import com.vardhan.common.web.BusinessException;
import com.vardhan.wallet.payment.GatewayPayment;
import com.vardhan.wallet.payment.MockGateway;
import com.vardhan.wallet.payment.RazorpayGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The demo bank's payment page, as an API. Exists only when the demo bank is the gateway
 * (app.payments.provider=mock); with Razorpay configured this endpoint is not registered at all.
 *
 * It plays the customer approving or declining at the bank. What it does NOT do is touch the wallet:
 * it builds a webhook in Razorpay's format, signs it, and sends it through the same
 * {@link PaymentWebhookService} a real gateway would call. So the demo exercises the real path.
 */
@RestController
@RequestMapping("/api/topups")
@Tag(name = "Top-ups")
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentController {

    public enum Outcome {
        /** The bank takes the money and the webhook arrives. */
        SUCCESS,
        /** The bank declines. */
        FAILURE,
        /** The bank takes the money but the webhook never arrives: reconciliation has to find it. */
        SUCCESS_WEBHOOK_LOST
    }

    public record MockPayRequest(@NotNull Outcome outcome) {
    }

    private final MockGateway bank;
    private final TopUpService topUpService;
    private final TopUpRepository topUpRepository;
    private final PaymentWebhookService webhookService;
    private final ObjectMapper objectMapper;

    public MockPaymentController(MockGateway bank, TopUpService topUpService, TopUpRepository topUpRepository,
                                 PaymentWebhookService webhookService, ObjectMapper objectMapper) {
        this.bank = bank;
        this.topUpService = topUpService;
        this.topUpRepository = topUpRepository;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{topUpId}/mock-pay")
    @Operation(summary = "Demo bank only: approve or decline the payment for one of my top-ups")
    public TopUpView pay(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID topUpId,
                         @Valid @RequestBody MockPayRequest request) {
        TopUp topUp = topUpRepository.findByIdAndUserId(topUpId, user.userId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "TOP_UP_NOT_FOUND", "Top-up not found"));
        if (!topUp.isAwaitingPayment()) {
            throw new BusinessException(HttpStatus.CONFLICT, "TOP_UP_NOT_PAYABLE",
                    "This top-up is " + topUp.getStatus() + " and can no longer be paid");
        }
        String paymentId = bank.newPaymentId();
        switch (request.outcome()) {
            case SUCCESS -> {
                bank.recordPaid(topUp.getProviderOrderId(), new GatewayPayment(paymentId, "upi"));
                deliverWebhook("payment.captured", topUp, paymentId, null);
            }
            case FAILURE -> deliverWebhook("payment.failed", topUp, paymentId, "Payment was declined by the bank");
            case SUCCESS_WEBHOOK_LOST -> bank.recordPaid(topUp.getProviderOrderId(), new GatewayPayment(paymentId, "upi"));
        }
        return topUpService.get(user.userId(), topUpId);
    }

    private void deliverWebhook(String event, TopUp topUp, String paymentId, String errorDescription) {
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("id", paymentId);
        payment.put("order_id", topUp.getProviderOrderId());
        payment.put("amount", RazorpayGateway.toMinorUnits(topUp.getAmount()));
        payment.put("currency", topUp.getCurrency());
        payment.put("status", errorDescription == null ? "captured" : "failed");
        payment.put("method", "upi");
        if (errorDescription != null) {
            payment.put("error_description", errorDescription);
        }
        Map<String, Object> body = Map.of(
                "entity", "event",
                "event", event,
                "payload", Map.of("payment", Map.of("entity", payment)));
        try {
            byte[] raw = objectMapper.writeValueAsBytes(body);
            webhookService.handle(raw, bank.signWebhook(raw));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not build the demo webhook", e);
        }
    }
}
