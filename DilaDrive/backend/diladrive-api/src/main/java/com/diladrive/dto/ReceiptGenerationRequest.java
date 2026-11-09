package com.diladrive.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReceiptGenerationRequest(
        @NotNull
        @AssertTrue(message = "Payment must be confirmed before a receipt can be generated.")
        Boolean paymentConfirmed,
        @NotBlank @Size(max = 100) String paymentReference,
        @Size(max = 100) String paymentMethod) {
}
