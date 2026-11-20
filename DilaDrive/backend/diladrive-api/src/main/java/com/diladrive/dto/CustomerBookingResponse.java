package com.diladrive.dto;

import com.diladrive.entity.Booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record CustomerBookingResponse(
        Long bookingId,
        String bookingType,
        String tripType,
        String notes,
        LocalDate tripDate,
        LocalTime pickupTime,
        String pickupAddress,
        String destinationAddress,
        String flightNo,
        Integer passengerCount,
        Integer luggageCount,
        String luggageSize,
        Double distanceKm,
        Double waitingHour,
        BigDecimal tripFare,
        String status,
        LocalDateTime createdAt,
        Integer smallCount, Integer mediumCount, Integer largeCount, Integer luggageUnits,
        Long durationSeconds, Double pickupLatitude, Double pickupLongitude,
        Double destinationLatitude, Double destinationLongitude,
        String contactName, String contactPhone, String rejectionReason,
        BigDecimal finalFare, BigDecimal payableFare, Boolean activeAssignment) {
    public CustomerBookingResponse(Long bookingId,String bookingType,String tripType,String notes,
            LocalDate tripDate,LocalTime pickupTime,String pickupAddress,String destinationAddress,String flightNo,
            Integer passengerCount,Integer luggageCount,String luggageSize,Double distanceKm,Double waitingHour,
            BigDecimal tripFare,String status,LocalDateTime createdAt) {
        this(bookingId,bookingType,tripType,notes,tripDate,pickupTime,pickupAddress,destinationAddress,flightNo,
            passengerCount,luggageCount,luggageSize,distanceKm,waitingHour,tripFare,status,createdAt,
            null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
    }

    public static CustomerBookingResponse from(Booking booking) {
        return from(booking, null, null, null);
    }

    public static CustomerBookingResponse from(Booking booking, BigDecimal finalFare, BigDecimal payableFare,
                                               Boolean activeAssignment) {
        return new CustomerBookingResponse(
                booking.getBookingId(),
                booking.getBookingType(),
                booking.getBookingTriptype(),
                booking.getBookingNotes(),
                booking.getBookingTripdate(),
                booking.getBookingPickuptime(),
                booking.getBookingPickupaddress(),
                booking.getBookingDestinationaddress(),
                booking.getBookingFlightno(),
                booking.getBookingPassengercount(),
                booking.getBookingLuggagecount(),
                booking.getBookingLuggagesize(),
                booking.getBookingDistancekm(),
                booking.getBookingWaitinghour(),
                booking.getBookingTripfare(),
                booking.getBookingStatus(),
                booking.getBookingCreatedat(),
                booking.getSmallCount(),booking.getMediumCount(),booking.getLargeCount(),booking.getLuggageUnits(),
                booking.getDurationSeconds(),booking.getPickupLatitude(),booking.getPickupLongitude(),
                booking.getDestinationLatitude(),booking.getDestinationLongitude(),
                booking.getContactName(),booking.getContactPhone(),booking.getRejectionReason(),
                finalFare,payableFare,activeAssignment);
    }
}
