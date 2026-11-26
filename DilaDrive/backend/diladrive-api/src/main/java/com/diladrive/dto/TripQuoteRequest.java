package com.diladrive.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

public record TripQuoteRequest(
        @NotBlank @Size(max=500) String pickupAddress,
        @NotBlank @Size(max=500) String destinationAddress,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double pickupLatitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double pickupLongitude,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double destinationLatitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double destinationLongitude,
        @NotNull LocalDateTime scheduledAt,
        @Min(1) @Max(100) int passengerCount,
        @Min(0) @Max(100) int smallCount,
        @Min(0) @Max(100) int mediumCount,
        @Min(0) @Max(100) int largeCount,
        @NotBlank @Pattern(regexp="NORMAL_TRANSFER|AIRPORT_PICKUP|AIRPORT_DROPOFF") String tripType,
        @Size(max=20) String flightNumber,
        @Size(max=1000) String additionalNotes,
        @Size(max=30) String vehicleCategory,
        @Size(max=256) String acceptedQuoteToken,
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate flightDate,
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalTime flightTime,
        @Null(message = "choose a vehicle category instead of a specific vehicle") @JsonInclude(JsonInclude.Include.NON_NULL) Long preferredVehicleId) {
    public TripQuoteRequest(String pickupAddress,String destinationAddress,Double pickupLatitude,Double pickupLongitude,
            Double destinationLatitude,Double destinationLongitude,LocalDateTime scheduledAt,int passengerCount,
            int smallCount,int mediumCount,int largeCount,String tripType,String flightNumber,String additionalNotes) {
        this(pickupAddress,destinationAddress,pickupLatitude,pickupLongitude,destinationLatitude,destinationLongitude,
                scheduledAt,passengerCount,smallCount,mediumCount,largeCount,tripType,flightNumber,additionalNotes,null,null,null,null,null);
    }
    public TripQuoteRequest(String pickupAddress,String destinationAddress,Double pickupLatitude,Double pickupLongitude,
            Double destinationLatitude,Double destinationLongitude,LocalDateTime scheduledAt,int passengerCount,
            int smallCount,int mediumCount,int largeCount,String tripType,String flightNumber,String additionalNotes,
            String vehicleCategory,String acceptedQuoteToken,LocalDate flightDate,LocalTime flightTime) {
        this(pickupAddress,destinationAddress,pickupLatitude,pickupLongitude,destinationLatitude,destinationLongitude,
                scheduledAt,passengerCount,smallCount,mediumCount,largeCount,tripType,flightNumber,additionalNotes,
                vehicleCategory,acceptedQuoteToken,flightDate,flightTime,null);
    }
    public String pricingKey() {
        String key = java.util.Arrays.asList(pickupAddress,destinationAddress,pickupLatitude,pickupLongitude,destinationLatitude,
                destinationLongitude,scheduledAt,passengerCount,smallCount,mediumCount,largeCount,tripType,flightNumber,additionalNotes)
                .stream().map(value -> value==null ? "-1:" : value.toString().length()+":"+value)
                .collect(java.util.stream.Collectors.joining());
        return flightDate == null && flightTime == null ? key : key + "|" + flightDate + "|" + flightTime;
    }
    public TripQuoteRequest accept(String category,String token) {
        return new TripQuoteRequest(pickupAddress,destinationAddress,pickupLatitude,pickupLongitude,destinationLatitude,destinationLongitude,
                scheduledAt,passengerCount,smallCount,mediumCount,largeCount,tripType,flightNumber,additionalNotes,category,token,flightDate,flightTime,preferredVehicleId);
    }
}
