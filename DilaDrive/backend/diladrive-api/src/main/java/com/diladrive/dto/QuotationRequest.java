package com.diladrive.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record QuotationRequest(
        @NotNull @Positive Long bookingId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2)
        BigDecimal amount,
        @Size(max = 5000) String remarks) {
}
