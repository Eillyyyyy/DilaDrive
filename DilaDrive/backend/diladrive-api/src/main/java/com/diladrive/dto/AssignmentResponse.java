package com.diladrive.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AssignmentResponse(
        Long assignmentId,
        Long bookingId,
        String customer,
        LocalDate tripDate,
        LocalTime pickupTime,
        String tripType,
        String flightNo,
        String pickupAddress,
        String destinationAddress,
        Integer passengerCount,
        Integer luggageCount,
        String luggageSize,
        Long driverId,
        String driverName,
        Long vehicleId,
        String vehiclePlate,
        String assignmentStatus,
        @JsonProperty("assignmentDate")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime assignmentDate) {
}
