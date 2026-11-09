package com.diladrive.service;

import com.diladrive.dto.AssignmentRequest;
import com.diladrive.entity.Assignment;
import com.diladrive.entity.Booking;
import com.diladrive.entity.Driver;
import com.diladrive.entity.Vehicle;
import com.diladrive.repository.AssignmentRepository;
import com.diladrive.repository.BookingRepository;
import com.diladrive.repository.DriverRepository;
import com.diladrive.repository.NotificationRepository;
import com.diladrive.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssignmentServiceTest {

    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final DriverRepository driverRepository = mock(DriverRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final NotificationRepository notificationRepository =
            mock(NotificationRepository.class);
    private final AssignmentService service = new AssignmentService(
            assignmentRepository,
            bookingRepository,
            driverRepository,
            vehicleRepository,
            notificationRepository);

    @Test
    void createsAssignmentAndStoresLinkedNotification() {
        Booking booking = confirmedBooking();
        Driver driver = driver(21L, "AVAILABLE");
        Vehicle vehicle = vehicle(31L, "AVAILABLE");
        when(bookingRepository.findByIdForUpdate(booking.getBookingId()))
                .thenReturn(Optional.of(booking));
        when(assignmentRepository.existsByBookingBookingIdAndAssignmentStatusNotIn(
                booking.getBookingId(), java.util.List.of("COMPLETED", "CANCELLED")))
                .thenReturn(false);
        when(driverRepository.findByIdForUpdate(driver.getDriverId()))
                .thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(vehicle.getVehicleId()))
                .thenReturn(Optional.of(vehicle));
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment saved = invocation.getArgument(0);
            saved.setAssignmentId(41L);
            return saved;
        });

        var result = service.createAssignment(
                new AssignmentRequest(booking.getBookingId(),
                        driver.getDriverId(), vehicle.getVehicleId()));

        assertEquals(41L, result.assignmentId());
        assertEquals("UNAVAILABLE", driver.getDriverAvailability());
        assertEquals("IN_USE", vehicle.getVehicleStatus());
        assertEquals(driver.getDriverId(), booking.getDriverId());
        assertEquals(vehicle.getVehicleId(), booking.getVehicleId());
        verify(notificationRepository).save(argThat(notification ->
                notification.getDriver() == driver
                        && "Assignment Update".equals(notification.getTitle())
                        && notification.getMessage().contains("assigned")));
    }

    @Test
    void rejectsUnavailableDriverWithoutCreatingAssignment() {
        Booking booking = confirmedBooking();
        Driver driver = driver(21L, "UNAVAILABLE");
        Vehicle vehicle = vehicle(31L, "AVAILABLE");
        when(bookingRepository.findByIdForUpdate(booking.getBookingId()))
                .thenReturn(Optional.of(booking));
        when(assignmentRepository.existsByBookingBookingIdAndAssignmentStatusNotIn(
                booking.getBookingId(), java.util.List.of("COMPLETED", "CANCELLED")))
                .thenReturn(false);
        when(driverRepository.findByIdForUpdate(driver.getDriverId()))
                .thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(vehicle.getVehicleId()))
                .thenReturn(Optional.of(vehicle));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createAssignment(new AssignmentRequest(
                        booking.getBookingId(), driver.getDriverId(), vehicle.getVehicleId())));

        assertEquals(409, error.getStatusCode().value());
        verify(assignmentRepository, never()).save(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void reassignsResourcesReleasesPreviousOnesAndNotifiesNewDriver() {
        Booking booking = confirmedBooking();
        Driver previousDriver = driver(21L, "UNAVAILABLE");
        Driver newDriver = driver(22L, "AVAILABLE");
        Vehicle previousVehicle = vehicle(31L, "IN_USE");
        Vehicle newVehicle = vehicle(32L, "AVAILABLE");
        Assignment assignment = new Assignment();
        assignment.setAssignmentId(41L);
        assignment.setBooking(booking);
        assignment.setDriver(previousDriver);
        assignment.setVehicle(previousVehicle);
        assignment.setAssignmentStatus("ASSIGNED");
        when(assignmentRepository.findByIdForUpdate(41L))
                .thenReturn(Optional.of(assignment));
        when(bookingRepository.findByIdForUpdate(booking.getBookingId()))
                .thenReturn(Optional.of(booking));
        when(driverRepository.findByIdForUpdate(newDriver.getDriverId()))
                .thenReturn(Optional.of(newDriver));
        when(driverRepository.findByIdForUpdate(previousDriver.getDriverId()))
                .thenReturn(Optional.of(previousDriver));
        when(vehicleRepository.findByIdForUpdate(newVehicle.getVehicleId()))
                .thenReturn(Optional.of(newVehicle));
        when(vehicleRepository.findByIdForUpdate(previousVehicle.getVehicleId()))
                .thenReturn(Optional.of(previousVehicle));
        when(assignmentRepository.save(assignment)).thenReturn(assignment);

        service.reassign(41L, new AssignmentRequest(
                booking.getBookingId(), newDriver.getDriverId(), newVehicle.getVehicleId()));

        assertEquals("AVAILABLE", previousDriver.getDriverAvailability());
        assertEquals("UNAVAILABLE", newDriver.getDriverAvailability());
        assertEquals("AVAILABLE", previousVehicle.getVehicleStatus());
        assertEquals("IN_USE", newVehicle.getVehicleStatus());
        assertEquals(newDriver.getDriverId(), booking.getDriverId());
        assertEquals(newVehicle.getVehicleId(), booking.getVehicleId());
        verify(notificationRepository).save(argThat(notification ->
                notification.getDriver() == newDriver
                        && notification.getMessage().contains("reassigned")));
    }

    private Booking confirmedBooking() {
        Booking booking = new Booking();
        booking.setBookingId(11L);
        booking.setCustId(7L);
        booking.setBookingStatus("CONFIRMED");
        booking.setBookingTripdate(LocalDate.of(2026, 11, 12));
        booking.setBookingPickuptime(LocalTime.of(9, 0));
        booking.setBookingPickupaddress("Pickup");
        booking.setBookingDestinationaddress("Destination");
        return booking;
    }

    private Driver driver(Long id, String availability) {
        Driver driver = new Driver();
        driver.setDriverId(id);
        driver.setDriverName("Test Driver");
        driver.setDriverAvailability(availability);
        return driver;
    }

    private Vehicle vehicle(Long id, String status) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(id);
        vehicle.setVehiclePlateno("TEST-123");
        vehicle.setVehicleStatus(status);
        return vehicle;
    }
}
