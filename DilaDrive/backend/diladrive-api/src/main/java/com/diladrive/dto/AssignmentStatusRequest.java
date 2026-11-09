package com.diladrive.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignmentStatusRequest(
        @NotBlank String status) {
}
