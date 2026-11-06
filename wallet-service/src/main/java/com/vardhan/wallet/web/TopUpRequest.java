package com.vardhan.wallet.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TopUpRequest(
        @NotNull
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal amount) {
}
