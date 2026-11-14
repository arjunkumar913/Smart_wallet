package com.vardhan.wallet.topup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoint (no JWT): the caller is the payment gateway, authenticated by its HMAC signature.
 */
@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Webhooks")
public class PaymentWebhookController {

    static final String SIGNATURE_HEADER = "X-Razorpay-Signature";

    private final PaymentWebhookService webhookService;

    public PaymentWebhookController(PaymentWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /** The body is taken as raw bytes: the signature covers the exact bytes sent, not re-serialized JSON. */
    @PostMapping("/razorpay")
    @SecurityRequirements
    @Operation(summary = "Payment gateway webhook (called by Razorpay, verified by HMAC-SHA256 signature)")
    public Map<String, String> razorpay(@RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
                                        @RequestBody byte[] body) {
        webhookService.handle(body, signature);
        return Map.of("status", "ok");
    }
}
