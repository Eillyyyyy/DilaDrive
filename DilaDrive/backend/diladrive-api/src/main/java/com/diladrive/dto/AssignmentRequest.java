package com.diladrive.dto;

import jakarta.validation.constraints.Positive;

public record AssignmentRequest(
        @Positive Long bookingId,
        @Positive Long driverId,
        @Positive Long vehicleId) {
}
