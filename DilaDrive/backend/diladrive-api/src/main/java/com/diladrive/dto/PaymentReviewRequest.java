package com.diladrive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentReviewRequest(
        @NotBlank @Size(max = 20) String decision,
        @Size(max = 1000) String reason) { }
