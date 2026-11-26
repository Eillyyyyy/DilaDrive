package com.diladrive.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record AssignmentRequest(@Positive Long bookingId, @Positive Long driverId,
        @Positive Long vehicleId, LocalDateTime scheduledEndAt, @Size(max = 500) String reason, boolean emergency) {
    public AssignmentRequest(Long bookingId, Long driverId, Long vehicleId, LocalDateTime scheduledEndAt, String reason) {
        this(bookingId, driverId, vehicleId, scheduledEndAt, reason, false);
    }
    public AssignmentRequest(Long bookingId, Long driverId, Long vehicleId, LocalDateTime scheduledEndAt) {
        this(bookingId, driverId, vehicleId, scheduledEndAt, null);
    }
    public AssignmentRequest(Long bookingId, Long driverId, Long vehicleId) {
        this(bookingId, driverId, vehicleId, null);
    }
}
