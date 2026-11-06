package com.vardhan.wallet.transfer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank @Email String toEmail,
        @NotNull
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal amount,
        @Size(max = 140) String note) {
}
