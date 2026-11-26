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
    void adminCannotCancelBookingThroughAssignmentStatus() {
        Booking booking = confirmedBooking();
        Assignment assignment = assignment(booking, driver(21L, "AVAILABLE"), vehicle(31L, "AVAILABLE"));
        when(assignmentRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(assignment));

        var exception = assertThrows(ResponseStatusException.class,
                () -> service.updateStatus(41L, "CANCELLED"));

        assertEquals(400, exception.getStatusCode().value());
        assertEquals("ASSIGNED", assignment.getAssignmentStatus());
        assertEquals("CONFIRMED", booking.getBookingStatus());
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(bookingRepository, notificationRepository);
    }

    @Test
    void rejectsUnavailableVehicleAndExistingResourceAssignments() {
        Booking booking = confirmedBooking();
        Driver driver = driver(21L, "AVAILABLE");
        Vehicle vehicle = vehicle(31L, "MAINTENANCE");
        when(bookingRepository.findByIdForUpdate(booking.getBookingId())).thenReturn(Optional.of(booking));
        when(driverRepository.findByIdForUpdate(driver.getDriverId())).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(vehicle.getVehicleId())).thenReturn(Optional.of(vehicle));
        var request = new AssignmentRequest(booking.getBookingId(), driver.getDriverId(), vehicle.getVehicleId(), LocalDateTime.of(2026, 11, 12, 11, 0));
        assertThrows(ResponseStatusException.class, () -> service.createAssignment(request));
        vehicle.setVehicleStatus("AVAILABLE");
        Assignment reserved = assignment(booking, driver, vehicle);
        reserved.setScheduledEndAt(LocalDateTime.of(2026, 11, 12, 12, 0));
        when(assignmentRepository.findResourceReservations(21L, 31L)).thenReturn(java.util.List.of(reserved));
        assertThrows(ResponseStatusException.class, () -> service.createAssignment(request));


        assertThrows(ResponseStatusException.class, () -> service.createAssignment(request));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void createsAssignmentAndStoresLinkedNotification() {
        Booking booking = confirmedBooking();
        Driver driver = driver(21L, "AVAILABLE");
        Vehicle vehicle = vehicle(31L, "AVAILABLE");
        when(bookingRepository.findByIdForUpdate(booking.getBookingId()))
                .thenReturn(Optional.of(booking));
        when(assignmentRepository.existsByBookingBookingIdAndAssignmentStatusNotIn(
                booking.getBookingId(), java.util.List.of("COMPLETED", "CANCELLED", "SUPERSEDED")))
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
                        driver.getDriverId(), vehicle.getVehicleId(), LocalDateTime.of(2026, 11, 12, 11, 0)));

        assertEquals(41L, result.assignmentId());
        assertEquals("Aina Logistics", result.customer());
        assertEquals("AVAILABLE", driver.getDriverAvailability());
        assertEquals("AVAILABLE", vehicle.getVehicleStatus());
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
                booking.getBookingId(), java.util.List.of("COMPLETED", "CANCELLED", "SUPERSEDED")))
                .thenReturn(false);
        when(driverRepository.findByIdForUpdate(driver.getDriverId()))
                .thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(vehicle.getVehicleId()))
                .thenReturn(Optional.of(vehicle));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createAssignment(new AssignmentRequest(
                        booking.getBookingId(), driver.getDriverId(), vehicle.getVehicleId(), LocalDateTime.of(2026, 11, 12, 11, 0))));

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
                booking.getBookingId(), java.util.List.of("COMPLETED", "CANCELLED", "SUPERSEDED")))
                .thenReturn(false);
        when(driverRepository.findByIdForUpdate(driver.getDriverId()))
                .thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(vehicle.getVehicleId()))
                .thenReturn(Optional.of(vehicle));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createAssignment(new AssignmentRequest(
                        booking.getBookingId(), driver.getDriverId(), vehicle.getVehicleId(), LocalDateTime.of(2026, 11, 12, 11, 0))));

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
                booking.getBookingId(), java.util.List.of("COMPLETED", "CANCELLED", "SUPERSEDED")))
                .thenReturn(false);
        when(driverRepository.findByIdForUpdate(driver.getDriverId()))
                .thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(vehicle.getVehicleId()))
                .thenReturn(Optional.of(vehicle));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createAssignment(new AssignmentRequest(
                        booking.getBookingId(), driver.getDriverId(), vehicle.getVehicleId(), LocalDateTime.of(2026, 11, 12, 11, 0))));

        assertEquals(409, error.getStatusCode().value());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void rejectsRoadTaxThatExpiresBeforeTheScheduledTripEnds() {
        Booking booking = confirmedBooking();
        Driver driver = driver(21L, "AVAILABLE");
        Vehicle vehicle = vehicle(31L, "AVAILABLE");
        vehicle.setVehicleRoadtaxexpiry(LocalDate.of(2026, 11, 11));
        when(bookingRepository.findByIdForUpdate(booking.getBookingId()))
                .thenReturn(Optional.of(booking));
        when(assignmentRepository.existsByBookingBookingIdAndAssignmentStatusNotIn(
                booking.getBookingId(), java.util.List.of("COMPLETED", "CANCELLED", "SUPERSEDED")))
                .thenReturn(false);
        when(driverRepository.findByIdForUpdate(driver.getDriverId()))
                .thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(vehicle.getVehicleId()))
                .thenReturn(Optional.of(vehicle));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createAssignment(new AssignmentRequest(
                        booking.getBookingId(), driver.getDriverId(), vehicle.getVehicleId(), LocalDateTime.of(2026, 11, 12, 11, 0))));

        assertEquals(409, error.getStatusCode().value());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void rejectsDriverLicenceThatExpiresBeforeTheScheduledTripEnds() {
        Booking booking = confirmedBooking();
        Driver driver = driver(21L, "AVAILABLE");
        driver.setDriverLicenseExpiry("2026-11-11");
        Vehicle vehicle = vehicle(31L, "AVAILABLE");
        when(bookingRepository.findByIdForUpdate(booking.getBookingId()))
                .thenReturn(Optional.of(booking));
        when(assignmentRepository.existsByBookingBookingIdAndAssignmentStatusNotIn(
                booking.getBookingId(), java.util.List.of("COMPLETED", "CANCELLED", "SUPERSEDED")))
                .thenReturn(false);
        when(driverRepository.findByIdForUpdate(driver.getDriverId()))
                .thenReturn(Optional.of(driver));
        when(vehicleRepository.findByIdForUpdate(vehicle.getVehicleId()))
                .thenReturn(Optional.of(vehicle));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createAssignment(new AssignmentRequest(
                        booking.getBookingId(), driver.getDriverId(), vehicle.getVehicleId(),
                        LocalDateTime.of(2026, 11, 12, 11, 0))));

        assertEquals(409, error.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertTrue(error.getReason().contains("licence expires before the trip ends"));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void serviceDateAloneDoesNotBlockAssignment() {
        Booking booking = confirmedBooking();
        Driver driver = driver(21L, "AVAILABLE");
        Vehicle vehicle = vehicle(31L, "AVAILABLE");
        vehicle.setVehicleServiceexpiry(LocalDate.of(2026, 11, 11));
        when(bookingRepository.findByIdForUpdate(booking.getBookingId()))
                .thenReturn(Optional.of(booking));
        when(assignmentRepository.existsByBookingBookingIdAndAssignmentStatusNotIn(
                booking.getBookingId(), java.util.List.of("COMPLETED", "CANCELLED", "SUPERSEDED")))
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

        var result = service.createAssignment(new AssignmentRequest(
                booking.getBookingId(), driver.getDriverId(), vehicle.getVehicleId(),
                LocalDateTime.of(2026, 11, 12, 11, 0)));

        assertEquals(41L, result.assignmentId());
        verify(assignmentRepository).save(any(Assignment.class));
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
        booking.setBookingTripdate(LocalDate.of(2035, 1, 1));
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
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> { Assignment saved=invocation.getArgument(0); if (saved.getAssignmentId()==null) saved.setAssignmentId(41L); return saved; });
        AssignmentSummaryProjection reassignedSummary =
                summary(41L, "Aina Logistics");
        when(assignmentRepository.findAssignmentSummaryById(41L))
                .thenReturn(Optional.of(reassignedSummary));

        service.reassign(41L, new AssignmentRequest(
                booking.getBookingId(), newDriver.getDriverId(), newVehicle.getVehicleId(), LocalDateTime.of(2035, 1, 1, 11, 0), "Vehicle maintenance"));

        assertEquals("UNAVAILABLE", previousDriver.getDriverAvailability());
        assertEquals("AVAILABLE", newDriver.getDriverAvailability());
        assertEquals("UNAVAILABLE", previousVehicle.getVehicleStatus());
        assertEquals("AVAILABLE", newVehicle.getVehicleStatus());
        assertEquals(newDriver.getDriverId(), booking.getDriverId());
        assertEquals(newVehicle.getVehicleId(), booking.getVehicleId());
        verify(notificationRepository).save(argThat(notification ->
                notification.getDriver() == newDriver
                        && notification.getMessage().contains("reassigned")));
    }

    @Test
    void supportsDriverOnlyReassignment() {
        Booking booking = confirmedBooking();
        booking.setBookingTripdate(LocalDate.of(2035, 1, 1));
        Driver previousDriver = driver(21L, "UNAVAILABLE");
        Driver newDriver = driver(22L, "AVAILABLE");
        Vehicle vehicle = vehicle(31L, "UNAVAILABLE");
        Vehicle linkedVehicle = vehicle(32L, "AVAILABLE");
        Assignment assignment = assignment(booking, previousDriver, vehicle);
        when(assignmentRepository.findByIdForUpdate(41L))
                .thenReturn(Optional.of(assignment));
        when(bookingRepository.findByIdForUpdate(booking.getBookingId()))
                .thenReturn(Optional.of(booking));
        when(driverRepository.findByIdForUpdate(newDriver.getDriverId()))
                .thenReturn(Optional.of(newDriver));
        when(driverRepository.findByIdForUpdate(previousDriver.getDriverId()))
                .thenReturn(Optional.of(previousDriver));
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> { Assignment saved=invocation.getArgument(0); if (saved.getAssignmentId()==null) saved.setAssignmentId(41L); return saved; });
        AssignmentSummaryProjection reassignedSummary =
                summary(41L, "Aina Logistics");
        when(assignmentRepository.findAssignmentSummaryById(41L))
                .thenReturn(Optional.of(reassignedSummary));

        service.reassign(41L, new AssignmentRequest(null, newDriver.getDriverId(), null, LocalDateTime.of(2035, 1, 1, 11, 0), "Vehicle maintenance"));

        assertEquals("UNAVAILABLE", previousDriver.getDriverAvailability());
        assertEquals("AVAILABLE", newDriver.getDriverAvailability());
        assertEquals("UNAVAILABLE", vehicle.getVehicleStatus());
        assertEquals(linkedVehicle.getVehicleId(), booking.getVehicleId());
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
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> { Assignment saved=invocation.getArgument(0); if (saved.getAssignmentId()==null) saved.setAssignmentId(41L); return saved; });
        AssignmentSummaryProjection completedSummary =
                summary(41L, "Aina Logistics", "COMPLETED");
        when(assignmentRepository.findAssignmentSummaryById(41L))
                .thenReturn(Optional.of(completedSummary));

        var early = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.completeAssignment(41L));
        assertEquals(409, early.getStatusCode().value());
        assignment.setAssignmentStatus("DROP");
        var result = service.completeAssignment(41L);

        assertEquals("COMPLETED", result.assignmentStatus());
        assertEquals("UNAVAILABLE", driver.getDriverAvailability());
        assertEquals("UNAVAILABLE", vehicle.getVehicleStatus());
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
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> { Assignment saved=invocation.getArgument(0); if (saved.getAssignmentId()==null) saved.setAssignmentId(41L); return saved; });
        AssignmentSummaryProjection updatedSummary =
                summary(41L, "Aina Logistics", "OTW");
        when(assignmentRepository.findAssignmentSummaryById(41L))
                .thenReturn(Optional.of(updatedSummary));

        var result = service.updateDriverStatus(41L, driver.getDriverId(), "OTW");

        assertEquals("OTW", result.assignmentStatus());
        assertEquals("OTW", assignment.getAssignmentStatus());
        assertEquals("CONFIRMED", booking.getBookingStatus());
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
        assertEquals("UNAVAILABLE", driver.getDriverAvailability());
        assertEquals("UNAVAILABLE", vehicle.getVehicleStatus());
        verify(assignmentRepository).save(assignment);
        verify(assignmentRepository, never()).delete(any());
    }

    @Test
    void legacyDriverWithoutVehicleCannotBeConfirmedAndReceivesRegistrationGuidance() {
        Booking booking = confirmedBooking(); booking.setBookingStatus("PENDING");
        Driver driver = driver(21L, "AVAILABLE");
        booking.setBookingTripfare(java.math.BigDecimal.TEN);
        when(bookingRepository.findByIdForUpdate(booking.getBookingId())).thenReturn(Optional.of(booking));
        when(driverRepository.findByIdForUpdate(driver.getDriverId())).thenReturn(Optional.of(driver));
        when(vehicleRepository.findByDriverId(driver.getDriverId())).thenReturn(java.util.List.of());
        var failure = assertThrows(ResponseStatusException.class, () -> service.confirmAndAssign(
            new AssignmentRequest(booking.getBookingId(), driver.getDriverId(), null,
                LocalDateTime.of(2026,11,12,11,0)), java.math.BigDecimal.TEN));
        assertEquals(409, failure.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertTrue(failure.getReason().contains("Complete vehicle registration"));
        assertEquals("PENDING", booking.getBookingStatus());
        verify(assignmentRepository, never()).save(any());
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
        vehicle.setDriverId(id - 10);
        when(vehicleRepository.findByDriverId(id - 10)).thenReturn(java.util.List.of(vehicle));
        when(vehicleRepository.findByIdForUpdate(id)).thenReturn(Optional.of(vehicle));
        return vehicle;
    }

    @Test
    void driverDetailOnlyUsesTheSessionScopedQuery() {
        AssignmentSummaryProjection own = summary(41L, "Customer", "ASSIGNED");
        when(assignmentRepository.findDriverAssignmentDetail(41L, 21L)).thenReturn(Optional.of(own));
        assertEquals(41L, service.getDriverAssignment(41L, 21L).assignmentId());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.getDriverAssignment(41L, 22L)).getStatusCode().value());
        verify(assignmentRepository, never()).findAssignmentSummaryById(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"WAITING,PICKUP", "PICKUP,DROP"})
    void driverCannotReachProofMilestoneWithoutPhoto(String previous, String next) {
        Booking booking = confirmedBooking();
        Assignment assignment = assignment(booking, driver(21L, "UNAVAILABLE"), vehicle(31L, "UNAVAILABLE"));
        assignment.setAssignmentStatus(previous);
        when(assignmentRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(assignment));
        when(bookingRepository.findByIdForUpdate(booking.getBookingId())).thenReturn(Optional.of(booking));
        var photos = mock(com.diladrive.repository.VerificationPhotoRepository.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "verificationPhotos", photos);

        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.updateDriverStatus(41L, 21L, next)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.updateStatus(41L, next)).getStatusCode().value());
        verify(photos, times(2)).existsByAssignmentAssignmentIdAndPhotoType(41L, next);
        assertEquals(previous, assignment.getAssignmentStatus());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void driverCannotReverseButRepeatIsIdempotent() {
        Assignment assignment = assignment(confirmedBooking(), driver(21L, "UNAVAILABLE"), vehicle(31L, "UNAVAILABLE"));
        assignment.setAssignmentStatus("WAITING");
        when(assignmentRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(assignment));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.updateDriverStatus(41L, 21L, "OTW")).getStatusCode().value());
        var unchanged = summary(41L, "Customer", "WAITING");
        when(assignmentRepository.findAssignmentSummaryById(41L)).thenReturn(Optional.of(unchanged));
        assertEquals("WAITING", service.updateDriverStatus(41L, 21L, "WAITING").assignmentStatus());
        verify(assignmentRepository, never()).save(any());
    }
}
