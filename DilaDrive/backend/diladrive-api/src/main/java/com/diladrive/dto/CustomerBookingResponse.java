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
        LocalDateTime createdAt) {

    public static CustomerBookingResponse from(Booking booking) {
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
                booking.getBookingCreatedat());
    }
}
