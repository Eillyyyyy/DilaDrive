package com.diladrive.dto;

import jakarta.validation.constraints.*;
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
        @Size(max=256) String acceptedQuoteToken) {
    public TripQuoteRequest(String pickupAddress,String destinationAddress,Double pickupLatitude,Double pickupLongitude,
            Double destinationLatitude,Double destinationLongitude,LocalDateTime scheduledAt,int passengerCount,
            int smallCount,int mediumCount,int largeCount,String tripType,String flightNumber,String additionalNotes) {
        this(pickupAddress,destinationAddress,pickupLatitude,pickupLongitude,destinationLatitude,destinationLongitude,
                scheduledAt,passengerCount,smallCount,mediumCount,largeCount,tripType,flightNumber,additionalNotes,null,null);
    }
    public String pricingKey() {
        return java.util.Arrays.asList(pickupAddress,destinationAddress,pickupLatitude,pickupLongitude,destinationLatitude,
                destinationLongitude,scheduledAt,passengerCount,smallCount,mediumCount,largeCount,tripType,flightNumber,additionalNotes)
                .stream().map(value -> value==null ? "-1:" : value.toString().length()+":"+value)
                .collect(java.util.stream.Collectors.joining());
    }
    public TripQuoteRequest accept(String category,String token) {
        return new TripQuoteRequest(pickupAddress,destinationAddress,pickupLatitude,pickupLongitude,destinationLatitude,destinationLongitude,
                scheduledAt,passengerCount,smallCount,mediumCount,largeCount,tripType,flightNumber,additionalNotes,category,token);
    }
}
