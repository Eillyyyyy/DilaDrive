package com.diladrive.dto;

import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record AssignmentRequest(@Positive Long bookingId, @Positive Long driverId,
        @Positive Long vehicleId, LocalDateTime scheduledEndAt) {
    public AssignmentRequest(Long bookingId, Long driverId, Long vehicleId) {
        this(bookingId, driverId, vehicleId, null);
    }
}
