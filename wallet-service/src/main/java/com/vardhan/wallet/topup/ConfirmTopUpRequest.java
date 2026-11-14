package com.vardhan.wallet.topup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What the gateway's checkout hands to the browser after a successful payment.
 * The signature proves it came from the gateway; the browser cannot forge it.
 */
public record ConfirmTopUpRequest(
        @NotBlank @Size(max = 100) String paymentId,
        @NotBlank @Size(max = 200) String signature) {
}
