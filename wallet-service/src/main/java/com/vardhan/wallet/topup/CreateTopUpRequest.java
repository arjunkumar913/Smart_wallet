package com.vardhan.wallet.topup;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateTopUpRequest(
        @NotNull
        @DecimalMin(value = "1.00", message = "must be at least 1.00")
        @Digits(integer = 17, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal amount) {
}
