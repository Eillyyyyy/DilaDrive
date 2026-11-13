package com.diladrive.dto;

import com.diladrive.entity.Booking;

public record CustomerBookingCreatedResponse(
        String bookingReference,
        String bookingStatus) {

    public static CustomerBookingCreatedResponse from(Booking booking) {
        return new CustomerBookingCreatedResponse(
                "BK-%06d".formatted(booking.getBookingId()),
                booking.getBookingStatus());
    }
}
