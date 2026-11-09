package com.diladrive.dto;

import jakarta.validation.constraints.NotNull;

public record AssignmentRequest(
        @NotNull Long bookingId,
        @NotNull Long driverId,
        @NotNull Long vehicleId) {
}
