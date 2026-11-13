package com.diladrive.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record CustomerBookingRequest(
        @NotBlank @Size(max = 30) String bookingType,
        @NotBlank @Size(max = 30) String tripType,
        @NotNull LocalDate bookingDate,
        LocalTime pickupTime,
        LocalTime landedTime,
        @Size(max = 20) String flightNumber,
        @NotBlank @Size(max = 500) String pickupAddress,
        @NotBlank @Size(max = 500) String destinationAddress,
        @NotNull @Min(1) Integer passengerCount,
        @NotNull @Min(0) Integer luggageCount,
        @Size(max = 20) String luggageSize,
        @Size(max = 1000) String additionalNotes) {
}
