package com.diladrive.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignmentStatusRequest(
        @NotBlank @jakarta.validation.constraints.Size(max = 16) String status) {
}
