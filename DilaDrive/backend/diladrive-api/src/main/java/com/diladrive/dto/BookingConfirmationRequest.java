package com.diladrive.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingConfirmationRequest(@NotNull @Positive Long driverId,
        @Positive Long vehicleId, @NotNull LocalDateTime scheduledEndAt,
        @DecimalMin(value = "0.01") BigDecimal tripFare) {}
