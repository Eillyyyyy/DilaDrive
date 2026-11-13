package com.diladrive.service;

import com.diladrive.dto.AssignmentRequest;
import com.diladrive.entity.Assignment;
import com.diladrive.entity.Booking;
import com.diladrive.entity.Driver;
import com.diladrive.entity.Vehicle;
import com.diladrive.repository.AssignmentRepository;
import com.diladrive.repository.AssignmentSummaryProjection;
import com.diladrive.repository.BookingRepository;
import com.diladrive.repository.DriverRepository;
import com.diladrive.repository.NotificationRepository;
import com.diladrive.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private final AssignmentServiceImpl service = new AssignmentServiceImpl(
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
        AssignmentSummaryProjection createdSummary = summary(41L, "Aina Logistics");
        when(assignmentRepository.findAssignmentSummaryById(41L))
                .thenReturn(Optional.of(createdSummary));

        var result = service.createAssignment(
                new AssignmentRequest(booking.getBookingId(),
                        driver.getDriverId(), vehicle.getVehicleId()));

        assertEquals(41L, result.assignmentId());
        assertEquals("Aina Logistics", result.customer());
        assertEquals("UNAVAILABLE", driver.getDriverAvailability());
        assertEquals("UNAVAILABLE", vehicle.getVehicleStatus());
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
    void rejectsVehicleWithInsufficientLuggageCapacity() {
        Booking booking = confirmedBooking();
        Driver driver = driver(21L, "AVAILABLE");
        Vehicle vehicle = vehicle(31L, "AVAILABLE");
        vehicle.setVehicleLuggageCapacity(1);
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
    }

    @Test
    void rejectsAvailableDriverWhoseAccountIsPending() {
        Booking booking = confirmedBooking();
        Driver driver = driver(21L, "AVAILABLE");
        driver.setDriverStatus("PENDING");
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
    }

    @Test
    void rejectsVehicleExpiredBeforeTheTripDate() {
        Booking booking = confirmedBooking();
        Driver driver = driver(21L, "AVAILABLE");
        Vehicle vehicle = vehicle(31L, "AVAILABLE");
        vehicle.setVehicleServiceexpiry(LocalDate.of(2026, 11, 11));
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
    }

    @Test
    void mapsDatabaseAssignmentDateIntoResponse() {
        LocalDateTime assignmentDate = LocalDateTime.of(2026, 11, 10, 5, 23, 16);
        AssignmentSummaryProjection projection = summary(41L, "Aina Logistics");
        when(projection.getAssignmentDate()).thenReturn(assignmentDate);
        when(projection.getTripType()).thenReturn("AIRPORT_PICKUP");
        when(projection.getFlightNo()).thenReturn("MH 123");
        when(projection.getLuggageCount()).thenReturn(2);
        when(projection.getLuggageSize()).thenReturn("MEDIUM");
        when(assignmentRepository.findAllAssignmentSummaries())
                .thenReturn(java.util.List.of(projection));

        var result = service.getAllAssignments().get(0);

        assertEquals(assignmentDate, result.assignmentDate());
        assertEquals("AIRPORT_PICKUP", result.tripType());
        assertEquals("MH 123", result.flightNo());
        assertEquals(2, result.luggageCount());
        assertEquals("MEDIUM", result.luggageSize());
    }

    @Test
    void reassignsResourcesReleasesPreviousOnesAndNotifiesNewDriver() {
        Booking booking = confirmedBooking();
        Driver previousDriver = driver(21L, "UNAVAILABLE");
        Driver newDriver = driver(22L, "AVAILABLE");
        Vehicle previousVehicle = vehicle(31L, "UNAVAILABLE");
        Vehicle newVehicle = vehicle(32L, "AVAILABLE");
        Assignment assignment = assignment(booking, previousDriver, previousVehicle);
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
        AssignmentSummaryProjection reassignedSummary =
                summary(41L, "Aina Logistics");
        when(assignmentRepository.findAssignmentSummaryById(41L))
                .thenReturn(Optional.of(reassignedSummary));

        service.reassign(41L, new AssignmentRequest(
                booking.getBookingId(), newDriver.getDriverId(), newVehicle.getVehicleId()));

        assertEquals("AVAILABLE", previousDriver.getDriverAvailability());
        assertEquals("UNAVAILABLE", newDriver.getDriverAvailability());
        assertEquals("AVAILABLE", previousVehicle.getVehicleStatus());
        assertEquals("UNAVAILABLE", newVehicle.getVehicleStatus());
        assertEquals(newDriver.getDriverId(), booking.getDriverId());
        assertEquals(newVehicle.getVehicleId(), booking.getVehicleId());
        verify(notificationRepository).save(argThat(notification ->
                notification.getDriver() == newDriver
                        && notification.getMessage().contains("reassigned")));
    }

    @Test
    void supportsDriverOnlyReassignment() {
        Booking booking = confirmedBooking();
        Driver previousDriver = driver(21L, "UNAVAILABLE");
        Driver newDriver = driver(22L, "AVAILABLE");
        Vehicle vehicle = vehicle(31L, "UNAVAILABLE");
        Assignment assignment = assignment(booking, previousDriver, vehicle);
        when(assignmentRepository.findByIdForUpdate(41L))
                .thenReturn(Optional.of(assignment));
        when(bookingRepository.findByIdForUpdate(booking.getBookingId()))
                .thenReturn(Optional.of(booking));
        when(driverRepository.findByIdForUpdate(newDriver.getDriverId()))
                .thenReturn(Optional.of(newDriver));
        when(driverRepository.findByIdForUpdate(previousDriver.getDriverId()))
                .thenReturn(Optional.of(previousDriver));
        when(assignmentRepository.save(assignment)).thenReturn(assignment);
        AssignmentSummaryProjection reassignedSummary =
                summary(41L, "Aina Logistics");
        when(assignmentRepository.findAssignmentSummaryById(41L))
                .thenReturn(Optional.of(reassignedSummary));

        service.reassign(41L, new AssignmentRequest(null, newDriver.getDriverId(), null));

        assertEquals("AVAILABLE", previousDriver.getDriverAvailability());
        assertEquals("UNAVAILABLE", newDriver.getDriverAvailability());
        assertEquals("UNAVAILABLE", vehicle.getVehicleStatus());
        assertEquals(vehicle.getVehicleId(), booking.getVehicleId());
    }

    @Test
    void completesAssignmentAndReleasesDriverAndVehicle() {
        Booking booking = confirmedBooking();
        booking.setDriverId(21L);
        booking.setVehicleId(31L);
        Driver driver = driver(21L, "UNAVAILABLE");
        Vehicle vehicle = vehicle(31L, "UNAVAILABLE");
        Assignment assignment = assignment(booking, driver, vehicle);
        when(assignmentRepository.findByIdForUpdate(41L))
                .thenReturn(Optional.of(assignment));
        when(bookingRepository.findByIdForUpdate(booking.getBookingId()))
                .thenReturn(Optional.of(booking));
        when(driverRepository.findByIdForUpdate(driver.getDriverId()))
                .thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(vehicle.getVehicleId()))
                .thenReturn(Optional.of(vehicle));
        when(assignmentRepository.save(assignment)).thenReturn(assignment);
        AssignmentSummaryProjection completedSummary =
                summary(41L, "Aina Logistics", "COMPLETED");
        when(assignmentRepository.findAssignmentSummaryById(41L))
                .thenReturn(Optional.of(completedSummary));

        var result = service.completeAssignment(41L);

        assertEquals("COMPLETED", result.assignmentStatus());
        assertEquals("AVAILABLE", driver.getDriverAvailability());
        assertEquals("AVAILABLE", vehicle.getVehicleStatus());
        assertEquals("COMPLETED", booking.getBookingStatus());
        assertNull(booking.getDriverId());
        assertNull(booking.getVehicleId());
    }

    @Test
    void driverCannotAdvanceAnotherDriversAssignment() {
        Booking booking = confirmedBooking();
        Assignment assignment = assignment(
                booking, driver(21L, "UNAVAILABLE"), vehicle(31L, "UNAVAILABLE"));
        when(assignmentRepository.findByIdForUpdate(41L))
                .thenReturn(Optional.of(assignment));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.updateDriverStatus(41L, 22L, "OTW"));

        assertEquals(404, error.getStatusCode().value());
        verify(bookingRepository, never()).findByIdForUpdate(any());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assignedDriverCanAdvanceToNextTripStatus() {
        Booking booking = confirmedBooking();
        Driver driver = driver(21L, "UNAVAILABLE");
        Assignment assignment = assignment(booking, driver, vehicle(31L, "UNAVAILABLE"));
        when(assignmentRepository.findByIdForUpdate(41L))
                .thenReturn(Optional.of(assignment));
        when(bookingRepository.findByIdForUpdate(booking.getBookingId()))
                .thenReturn(Optional.of(booking));
        when(assignmentRepository.save(assignment)).thenReturn(assignment);
        AssignmentSummaryProjection updatedSummary =
                summary(41L, "Aina Logistics", "OTW");
        when(assignmentRepository.findAssignmentSummaryById(41L))
                .thenReturn(Optional.of(updatedSummary));

        var result = service.updateDriverStatus(41L, driver.getDriverId(), "OTW");

        assertEquals("OTW", result.assignmentStatus());
        assertEquals("OTW", assignment.getAssignmentStatus());
        assertEquals("OTW", booking.getBookingStatus());
        verify(notificationRepository).save(argThat(notification ->
                "CUSTOMER".equals(notification.getRecipientRole())
                        && booking.getCustId().equals(notification.getRecipientId())
                        && "TRIP_STATUS".equals(notification.getNotificationType())));
    }

    @Test
    void driverCannotSkipTripStatusSteps() {
        Booking booking = confirmedBooking();
        Assignment assignment = assignment(
                booking, driver(21L, "UNAVAILABLE"), vehicle(31L, "UNAVAILABLE"));
        when(assignmentRepository.findByIdForUpdate(41L))
                .thenReturn(Optional.of(assignment));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.updateDriverStatus(41L, 21L, "PICKUP"));

        assertEquals(409, error.getStatusCode().value());
        verify(bookingRepository, never()).findByIdForUpdate(any());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void deleteCancelsAssignmentAndReleasesResourcesWithoutRemovingHistory() {
        Booking booking = confirmedBooking();
        Driver driver = driver(21L, "UNAVAILABLE");
        Vehicle vehicle = vehicle(31L, "UNAVAILABLE");
        Assignment assignment = assignment(booking, driver, vehicle);
        when(assignmentRepository.findByIdForUpdate(41L))
                .thenReturn(Optional.of(assignment));
        when(bookingRepository.findByIdForUpdate(booking.getBookingId()))
                .thenReturn(Optional.of(booking));
        when(driverRepository.findByIdForUpdate(driver.getDriverId()))
                .thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(vehicle.getVehicleId()))
                .thenReturn(Optional.of(vehicle));

        service.deleteAssignment(41L);

        assertEquals("CANCELLED", assignment.getAssignmentStatus());
        assertEquals("AVAILABLE", driver.getDriverAvailability());
        assertEquals("AVAILABLE", vehicle.getVehicleStatus());
        verify(assignmentRepository).save(assignment);
        verify(assignmentRepository, never()).delete(any());
    }

    private AssignmentSummaryProjection summary(Long assignmentId, String customer) {
        return summary(assignmentId, customer, "ASSIGNED");
    }

    private AssignmentSummaryProjection summary(
            Long assignmentId,
            String customer,
            String assignmentStatus) {
        AssignmentSummaryProjection projection = mock(AssignmentSummaryProjection.class);
        when(projection.getAssignmentId()).thenReturn(assignmentId);
        when(projection.getBookingId()).thenReturn(11L);
        when(projection.getCustomer()).thenReturn(customer);
        when(projection.getTripDate()).thenReturn(LocalDate.of(2026, 11, 12));
        when(projection.getPickupTime()).thenReturn(LocalTime.of(9, 0));
        when(projection.getTripType()).thenReturn("NORMAL_TRANSFER");
        when(projection.getFlightNo()).thenReturn(null);
        when(projection.getPickupAddress()).thenReturn("Pickup");
        when(projection.getDestinationAddress()).thenReturn("Destination");
        when(projection.getPassengerCount()).thenReturn(1);
        when(projection.getLuggageCount()).thenReturn(0);
        when(projection.getLuggageSize()).thenReturn(null);
        when(projection.getDriverId()).thenReturn(21L);
        when(projection.getDriverName()).thenReturn("Test Driver");
        when(projection.getVehicleId()).thenReturn(31L);
        when(projection.getVehiclePlate()).thenReturn("TEST-123");
        when(projection.getAssignmentStatus()).thenReturn(assignmentStatus);
        when(projection.getAssignmentDate()).thenReturn(LocalDateTime.now());
        return projection;
    }

    private Assignment assignment(Booking booking, Driver driver, Vehicle vehicle) {
        Assignment assignment = new Assignment();
        assignment.setAssignmentId(41L);
        assignment.setBooking(booking);
        assignment.setDriver(driver);
        assignment.setVehicle(vehicle);
        assignment.setAssignmentStatus("ASSIGNED");
        return assignment;
    }

    private Booking confirmedBooking() {
        Booking booking = new Booking();
        booking.setBookingId(11L);
        booking.setCustId(7L);
        booking.setBookingStatus("CONFIRMED");
        booking.setBookingType("Sedan");
        booking.setBookingTripdate(LocalDate.of(2026, 11, 12));
        booking.setBookingPickuptime(LocalTime.of(9, 0));
        booking.setBookingPickupaddress("Pickup");
        booking.setBookingDestinationaddress("Destination");
        booking.setBookingPassengercount(2);
        booking.setBookingLuggagecount(2);
        return booking;
    }

    private Driver driver(Long id, String availability) {
        Driver driver = new Driver();
        driver.setDriverId(id);
        driver.setDriverName("Test Driver");
        driver.setDriverStatus("ACTIVE");
        driver.setDriverAvailability(availability);
        return driver;
    }

    private Vehicle vehicle(Long id, String status) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(id);
        vehicle.setVehiclePlateno("TEST-123");
        vehicle.setVehicleType("Sedan");
        vehicle.setVehicleCapacity(4);
        vehicle.setVehicleLuggageCapacity(4);
        vehicle.setVehicleStatus(status);
        return vehicle;
    }
}
