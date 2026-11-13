package com.diladrive.service;

import com.diladrive.entity.Booking;
import com.diladrive.repository.AssignmentRepository;
import com.diladrive.repository.BookingRepository;
import com.diladrive.repository.CustomerRepository;
import com.diladrive.repository.DriverRepository;
import com.diladrive.repository.VehicleRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerBookingQueryTest {

    @Test
    void retrievesOnlyTheRequestedCustomersBookingsAndMapsApiFields() {
        BookingRepository bookings = mock(BookingRepository.class);
        Booking booking = new Booking();
        booking.setBookingId(17L);
        booking.setCustId(42L);
        booking.setBookingType("Sedan");
        booking.setBookingTriptype("NORMAL_TRANSFER");
        booking.setBookingNotes("Customer notes");
        booking.setBookingTripdate(LocalDate.of(2026, 12, 1));
        booking.setBookingPickuptime(LocalTime.of(9, 30));
        booking.setBookingPickupaddress("Pickup");
        booking.setBookingDestinationaddress("Destination");
        booking.setBookingFlightno("AB123");
        booking.setBookingPassengercount(3);
        booking.setBookingLuggagecount(2);
        booking.setBookingLuggagesize("MEDIUM");
        booking.setBookingDistancekm(145.5);
        booking.setBookingWaitinghour(0.5);
        booking.setBookingTripfare(new BigDecimal("240.75"));
        booking.setBookingStatus("CONFIRMED");
        booking.setBookingCreatedat(LocalDateTime.of(2026, 11, 1, 10, 0));
        when(bookings.findCustomerBookingsNewestFirst(42L))
                .thenReturn(List.of(booking));

        BookingService service = new BookingService(
                bookings,
                mock(CustomerRepository.class),
                mock(DriverRepository.class),
                mock(VehicleRepository.class),
                mock(NotificationService.class),
                mock(AssignmentRepository.class));

        var result = service.getCustomerBookings(42L);

        verify(bookings).findCustomerBookingsNewestFirst(42L);
        assertEquals(1, result.size());
        assertEquals(17L, result.get(0).bookingId());
        assertEquals("Sedan", result.get(0).bookingType());
        assertEquals("NORMAL_TRANSFER", result.get(0).tripType());
        assertEquals("Customer notes", result.get(0).notes());
        assertEquals(LocalDate.of(2026, 12, 1), result.get(0).tripDate());
        assertEquals(LocalTime.of(9, 30), result.get(0).pickupTime());
        assertEquals("Pickup", result.get(0).pickupAddress());
        assertEquals("Destination", result.get(0).destinationAddress());
        assertEquals("AB123", result.get(0).flightNo());
        assertEquals(3, result.get(0).passengerCount());
        assertEquals(2, result.get(0).luggageCount());
        assertEquals("MEDIUM", result.get(0).luggageSize());
        assertEquals(145.5, result.get(0).distanceKm(), 0.0);
        assertEquals(0.5, result.get(0).waitingHour(), 0.0);
        assertEquals(new BigDecimal("240.75"), result.get(0).tripFare());
        assertEquals("CONFIRMED", result.get(0).status());
    }
}
