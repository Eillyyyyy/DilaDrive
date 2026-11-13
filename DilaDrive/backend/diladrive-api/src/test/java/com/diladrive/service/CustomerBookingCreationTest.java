package com.diladrive.service;

import com.diladrive.dto.CustomerBookingRequest;
import com.diladrive.entity.Booking;
import com.diladrive.entity.Customer;
import com.diladrive.entity.Vehicle;
import com.diladrive.entity.Driver;
import com.diladrive.repository.BookingRepository;
import com.diladrive.repository.CustomerRepository;
import com.diladrive.repository.DriverRepository;
import com.diladrive.repository.VehicleRepository;
import com.diladrive.repository.AssignmentRepository;
import com.diladrive.repository.BookingRequestIdempotencyRepository;
import com.diladrive.entity.BookingRequestIdempotency;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerBookingCreationTest {
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final DriverRepository drivers = mock(DriverRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final BookingRequestIdempotencyRepository idempotency =
            mock(BookingRequestIdempotencyRepository.class);
    private final BookingService service = new BookingService(
            bookings, customers, drivers, vehicles, notifications, assignments,
            idempotency);

    @Test
    void createsPendingBookingForVerifiedCustomerWithoutTrustingClientFareOrOwnership() {
        Customer customer = new Customer();
        customer.setCustId(42L);
        customer.setCustStatus("ACTIVE");
        when(customers.findById(42L)).thenReturn(Optional.of(customer));
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleType("Sedan");
        when(vehicles.findAll()).thenReturn(java.util.List.of(vehicle));
        arrangeAvailableVehicle("Sedan");
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setBookingId(17L);
            return booking;
        });
        CustomerBookingRequest request = request();

        var response = service.createCustomerBooking(42L, request);

        assertEquals("BK-000017", response.bookingReference());
        assertEquals("PENDING_VERIFICATION", response.bookingStatus());
        var saved = org.mockito.ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(saved.capture());
        assertEquals(42L, saved.getValue().getCustId());
        assertEquals("Sedan", saved.getValue().getBookingType());
        assertEquals("AIRPORT_PICKUP", saved.getValue().getBookingTriptype());
        verify(notifications).createCustomerNotification(
                eq(42L),
                eq("Booking request received"),
                contains("BK-000017"),
                eq("BOOKING"));
        assertNull(saved.getValue().getBookingTripfare());
        assertNull(saved.getValue().getVehicleId());
        assertNull(saved.getValue().getDriverId());
    }

    @Test
    void dropsFlightNumberFromNormalTransferOnCreate() {
        Customer customer = new Customer();
        customer.setCustId(42L);
        customer.setCustStatus("ACTIVE");
        when(customers.findById(42L)).thenReturn(Optional.of(customer));
        when(vehicles.findAll()).thenReturn(java.util.List.of(vehicle("Sedan")));
        arrangeAvailableVehicle("Sedan");
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setBookingId(18L);
            return booking;
        });
        CustomerBookingRequest request = new CustomerBookingRequest(
                "Sedan", "NORMAL_TRANSFER", LocalDate.now().plusDays(2),
                LocalTime.of(10, 30), null, "MH123", "Pickup", "Destination",
                2, 1, "SMALL", null);

        service.createCustomerBooking(42L, request);

        var saved = org.mockito.ArgumentCaptor.forClass(Booking.class);
        verify(bookings).save(saved.capture());
        assertNull(saved.getValue().getBookingFlightno());
    }

    @Test
    void retriesWithSameIdempotencyKeyReturnOriginalBookingWithoutRepeatingNotification() throws Exception {
        Customer customer = new Customer();
        customer.setCustId(42L);
        customer.setCustStatus("ACTIVE");
        when(customers.findById(42L)).thenReturn(Optional.of(customer));
        when(vehicles.findAll()).thenReturn(java.util.List.of(vehicle("Sedan")));
        arrangeAvailableVehicle("Sedan");

        CustomerBookingRequest request = request();
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(request.toString().getBytes(StandardCharsets.UTF_8));
        String hash = java.util.HexFormat.of().formatHex(digest);
        BookingRequestIdempotency record = new BookingRequestIdempotency();
        record.setCustomerId(42L);
        record.setIdempotencyKey("retry-key");
        record.setRequestHash(hash);
        when(idempotency.findForUpdate(42L, "retry-key"))
                .thenReturn(Optional.of(record));
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            saved.setBookingId(17L);
            return saved;
        });

        var first = service.createCustomerBooking(42L, request, "retry-key");
        var retry = service.createCustomerBooking(42L, request, "retry-key");

        assertEquals("BK-000017", first.bookingReference());
        assertEquals(first, retry);
        verify(bookings, times(1)).save(any(Booking.class));
        verify(idempotency, times(1)).save(record);
        verify(notifications, times(1)).createCustomerNotification(
                eq(42L), eq("Booking request received"), contains("BK-000017"), eq("BOOKING"));
    }

    @Test
    void rejectsReusingIdempotencyKeyForChangedBookingDetails() throws Exception {
        Customer customer = new Customer();
        customer.setCustId(42L);
        customer.setCustStatus("ACTIVE");
        when(customers.findById(42L)).thenReturn(Optional.of(customer));
        CustomerBookingRequest original = request();
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(original.toString().getBytes(StandardCharsets.UTF_8));
        BookingRequestIdempotency record = new BookingRequestIdempotency();
        record.setRequestHash(java.util.HexFormat.of().formatHex(digest));
        when(idempotency.findForUpdate(42L, "retry-key"))
                .thenReturn(Optional.of(record));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.createCustomerBooking(42L,
                        new CustomerBookingRequest("Sedan", "AIRPORT_PICKUP",
                                LocalDate.now().plusDays(2), null, LocalTime.of(10, 30),
                                "MH123", "Different pickup", "Destination", 2, 1,
                                "SMALL", null), "retry-key"));

        assertEquals(409, error.getStatusCode().value());
        verify(bookings, never()).save(any());
    }

    @Test
    void rejectsUnverifiedCustomerWithoutSavingBooking() {
        Customer customer = new Customer();
        customer.setCustId(42L);
        customer.setCustStatus("PENDING");
        when(customers.findById(42L)).thenReturn(Optional.of(customer));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createCustomerBooking(42L, request()));

        assertEquals(403, exception.getStatusCode().value());
        verify(bookings, never()).save(any());
    }

    @Test
    void rejectsBookingWhenAvailabilityChangedAfterRecommendation() {
        Customer customer = new Customer();
        customer.setCustId(42L);
        customer.setCustStatus("ACTIVE");
        when(customers.findById(42L)).thenReturn(Optional.of(customer));
        when(vehicles.findAll()).thenReturn(java.util.List.of(vehicle("Sedan")));
        when(vehicles.findByVehicleStatusIgnoreCase("AVAILABLE"))
                .thenReturn(java.util.List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createCustomerBooking(42L, request()));

        assertEquals(409, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("no longer available"));
        verify(bookings, never()).save(any());
    }

    @Test
    void adminMustSetFinalFareBeforeConfirming() {
        Booking booking = new Booking();
        booking.setBookingId(17L);
        booking.setBookingStatus("PENDING_VERIFICATION");
        when(bookings.findByIdForUpdate(17L)).thenReturn(Optional.of(booking));
        when(bookings.save(booking)).thenReturn(booking);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.verifyBooking(17L));
        assertEquals(409, exception.getStatusCode().value());

        service.updateBookingFare(17L, new BigDecimal("145.00"));
        Booking confirmed = service.verifyBooking(17L);

        assertEquals("CONFIRMED", confirmed.getBookingStatus());
        assertEquals(new BigDecimal("145.00"), confirmed.getBookingTripfare());
    }

    @Test
    void customerCanCancelTheirPendingBooking() {
        Booking booking = new Booking();
        booking.setBookingId(17L);
        booking.setCustId(42L);
        booking.setBookingStatus("PENDING_VERIFICATION");
        when(bookings.findByIdForUpdate(17L)).thenReturn(Optional.of(booking));
        when(assignments.existsByBookingBookingIdAndAssignmentStatusNotIn(
                17L, java.util.List.of("COMPLETED", "CANCELLED")))
                .thenReturn(false);
        when(bookings.save(booking)).thenReturn(booking);

        var result = service.cancelCustomerBooking(42L, 17L);

        assertEquals("CANCELLED", result.status());
        verify(notifications).createCustomerNotification(
                42L,
                "Booking cancelled",
                "Your booking BK-000017 has been cancelled.",
                "BOOKING_CANCELLED");
    }

    @Test
    void customerCanEditTheirPendingBookingAndStaleFareIsCleared() {
        Customer customer = new Customer();
        customer.setCustId(42L);
        customer.setCustStatus("ACTIVE");
        when(customers.findById(42L)).thenReturn(Optional.of(customer));
        Booking booking = new Booking();
        booking.setBookingId(17L);
        booking.setCustId(42L);
        booking.setBookingStatus("PENDING_VERIFICATION");
        booking.setBookingTripfare(new BigDecimal("100.00"));
        when(bookings.findByIdForUpdate(17L)).thenReturn(Optional.of(booking));
        when(assignments.existsByBookingBookingIdAndAssignmentStatusNotIn(
                17L, java.util.List.of("COMPLETED", "CANCELLED")))
                .thenReturn(false);
        when(vehicles.findAll()).thenReturn(java.util.List.of(vehicle("Sedan")));
        arrangeAvailableVehicle("Sedan");
        when(bookings.save(booking)).thenReturn(booking);

        var result = service.updateCustomerBooking(42L, 17L, request());

        assertEquals("PENDING_VERIFICATION", result.status());
        assertEquals("Pickup", booking.getBookingPickupaddress());
        assertEquals("Destination", booking.getBookingDestinationaddress());
        assertEquals("AIRPORT_PICKUP", booking.getBookingTriptype());
        assertNull(booking.getBookingTripfare());
        verify(notifications).createCustomerNotification(
                eq(42L), eq("Booking request updated"),
                contains("BK-000017"), eq("BOOKING_UPDATED"));
    }

    @Test
    void dropsFlightNumberFromNormalTransferWhenEditingPendingBooking() {
        Customer customer = new Customer();
        customer.setCustId(42L);
        customer.setCustStatus("ACTIVE");
        when(customers.findById(42L)).thenReturn(Optional.of(customer));
        Booking booking = new Booking();
        booking.setBookingId(17L);
        booking.setCustId(42L);
        booking.setBookingStatus("PENDING_VERIFICATION");
        when(bookings.findByIdForUpdate(17L)).thenReturn(Optional.of(booking));
        when(assignments.existsByBookingBookingIdAndAssignmentStatusNotIn(
                17L, java.util.List.of("COMPLETED", "CANCELLED")))
                .thenReturn(false);
        when(vehicles.findAll()).thenReturn(java.util.List.of(vehicle("Sedan")));
        arrangeAvailableVehicle("Sedan");
        when(bookings.save(booking)).thenReturn(booking);
        CustomerBookingRequest request = new CustomerBookingRequest(
                "Sedan", "NORMAL_TRANSFER", LocalDate.now().plusDays(2),
                LocalTime.of(10, 30), null, "MH123", "Pickup", "Destination",
                2, 1, "SMALL", null);

        service.updateCustomerBooking(42L, 17L, request);

        assertNull(booking.getBookingFlightno());
    }

    @Test
    void customerCannotEditAnotherCustomersBooking() {
        Customer customer = new Customer();
        customer.setCustId(43L);
        customer.setCustStatus("ACTIVE");
        when(customers.findById(43L)).thenReturn(Optional.of(customer));
        Booking booking = new Booking();
        booking.setBookingId(17L);
        booking.setCustId(42L);
        booking.setBookingStatus("PENDING_VERIFICATION");
        when(bookings.findByIdForUpdate(17L)).thenReturn(Optional.of(booking));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateCustomerBooking(43L, 17L, request()));

        assertEquals(404, error.getStatusCode().value());
        verify(bookings, never()).save(any());
    }

    @Test
    void customerCannotEditAConfirmedOrAssignedBooking() {
        Customer customer = new Customer();
        customer.setCustId(42L);
        customer.setCustStatus("ACTIVE");
        when(customers.findById(42L)).thenReturn(Optional.of(customer));
        Booking booking = new Booking();
        booking.setBookingId(17L);
        booking.setCustId(42L);
        booking.setBookingStatus("CONFIRMED");
        when(bookings.findByIdForUpdate(17L)).thenReturn(Optional.of(booking));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateCustomerBooking(42L, 17L, request()));

        assertEquals(409, error.getStatusCode().value());
        verify(bookings, never()).save(any());
    }

    @Test
    void customerCannotEditPendingBookingWithActiveAssignment() {
        Customer customer = new Customer();
        customer.setCustId(42L);
        customer.setCustStatus("ACTIVE");
        when(customers.findById(42L)).thenReturn(Optional.of(customer));
        Booking booking = new Booking();
        booking.setBookingId(17L);
        booking.setCustId(42L);
        booking.setBookingStatus("PENDING_VERIFICATION");
        when(bookings.findByIdForUpdate(17L)).thenReturn(Optional.of(booking));
        when(assignments.existsByBookingBookingIdAndAssignmentStatusNotIn(
                17L, java.util.List.of("COMPLETED", "CANCELLED")))
                .thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateCustomerBooking(42L, 17L, request()));

        assertEquals(409, error.getStatusCode().value());
        verify(bookings, never()).save(any());
    }

    @Test
    void customerCannotCancelAnotherCustomersBooking() {
        Booking booking = new Booking();
        booking.setBookingId(17L);
        booking.setCustId(42L);
        booking.setBookingStatus("PENDING_VERIFICATION");
        when(bookings.findByIdForUpdate(17L)).thenReturn(Optional.of(booking));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.cancelCustomerBooking(43L, 17L));

        assertEquals(404, error.getStatusCode().value());
        verify(bookings, never()).save(any());
        verify(notifications, never()).createCustomerNotification(
                any(), any(), any(), any());
    }

    @Test
    void customerCannotCancelBookingWithAnActiveAssignment() {
        Booking booking = new Booking();
        booking.setBookingId(17L);
        booking.setCustId(42L);
        booking.setBookingStatus("CONFIRMED");
        when(bookings.findByIdForUpdate(17L)).thenReturn(Optional.of(booking));
        when(assignments.existsByBookingBookingIdAndAssignmentStatusNotIn(
                17L, java.util.List.of("COMPLETED", "CANCELLED")))
                .thenReturn(true);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.cancelCustomerBooking(42L, 17L));

        assertEquals(409, error.getStatusCode().value());
        verify(bookings, never()).save(any());
        verify(notifications, never()).createCustomerNotification(
                any(), any(), any(), any());
    }

    @Test
    void recommendsOnlyCurrentlyAvailableVehicleWithRecordedCapacity() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(6L);
        vehicle.setVehicleModel("Fleet MPV");
        vehicle.setVehicleType("MPV");
        vehicle.setVehicleCapacity(7);
        vehicle.setVehicleLuggageCapacity(5);
        vehicle.setVehicleStatus("AVAILABLE");
        vehicle.setDriverId(9L);
        Driver driver = new Driver();
        driver.setDriverId(9L);
        driver.setDriverStatus("ACTIVE");
        driver.setDriverAvailability("AVAILABLE");
        when(vehicles.findByVehicleStatusIgnoreCase("AVAILABLE"))
                .thenReturn(java.util.List.of(vehicle));
        when(drivers.findById(9L)).thenReturn(Optional.of(driver));

        var recommendation = service.recommendVehicle(
                "Pickup", "Destination", LocalDate.now().plusDays(1).atTime(10, 0),
                3, 2);

        assertTrue(recommendation.available());
        assertEquals(6L, recommendation.vehicle().vehicleId());
        assertEquals(5, recommendation.vehicle().luggageCapacity());
    }

    @Test
    void doesNotRecommendVehicleLinkedToPendingDriver() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(6L);
        vehicle.setVehicleModel("Fleet MPV");
        vehicle.setVehicleType("MPV");
        vehicle.setVehicleCapacity(7);
        vehicle.setVehicleLuggageCapacity(5);
        vehicle.setVehicleStatus("AVAILABLE");
        vehicle.setDriverId(9L);
        Driver driver = new Driver();
        driver.setDriverId(9L);
        driver.setDriverStatus("PENDING");
        driver.setDriverAvailability("AVAILABLE");
        when(vehicles.findByVehicleStatusIgnoreCase("AVAILABLE"))
                .thenReturn(java.util.List.of(vehicle));
        when(drivers.findById(9L)).thenReturn(Optional.of(driver));

        var recommendation = service.recommendVehicle(
                "Pickup", "Destination", LocalDate.now().plusDays(1).atTime(10, 0),
                3, 2);

        assertFalse(recommendation.available());
        assertNull(recommendation.vehicle());
    }

    private CustomerBookingRequest request() {
        return new CustomerBookingRequest(
                "Sedan", "AIRPORT_PICKUP", LocalDate.now().plusDays(2),
                null, LocalTime.of(10, 30), "MH123", "Pickup", "Destination",
                2, 1, "SMALL", null);
    }

    private Vehicle vehicle(String type) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleType(type);
        return vehicle;
    }

    private void arrangeAvailableVehicle(String type) {
        Vehicle vehicle = vehicle(type);
        vehicle.setVehicleId(6L);
        vehicle.setVehicleCapacity(4);
        vehicle.setVehicleLuggageCapacity(3);
        vehicle.setVehicleStatus("AVAILABLE");
        vehicle.setDriverId(9L);
        Driver driver = new Driver();
        driver.setDriverId(9L);
        driver.setDriverStatus("ACTIVE");
        driver.setDriverAvailability("AVAILABLE");
        when(vehicles.findByVehicleStatusIgnoreCase("AVAILABLE"))
                .thenReturn(java.util.List.of(vehicle));
        when(drivers.findById(9L)).thenReturn(Optional.of(driver));
    }
}
