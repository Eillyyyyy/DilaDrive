package com.diladrive.dto;

import com.diladrive.entity.DocumentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DocumentRequest(
        @NotNull DocumentType documentType,
        @NotNull @Positive Long bookingId,
        @NotNull @Positive Long custId,
        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 8, fraction = 2)
        BigDecimal amount,
        @Size(max = 5000) String remarks) {
}
