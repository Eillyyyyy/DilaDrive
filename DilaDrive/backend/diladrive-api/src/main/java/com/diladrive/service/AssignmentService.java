package com.diladrive.service;

import com.diladrive.dto.AssignmentRequest;
import com.diladrive.dto.AssignmentResponse;
import com.diladrive.entity.Assignment;
import com.diladrive.entity.Booking;
import com.diladrive.entity.Driver;
import com.diladrive.entity.Notification;
import com.diladrive.entity.Vehicle;
import com.diladrive.repository.AssignmentRepository;
import com.diladrive.repository.BookingRepository;
import com.diladrive.repository.DriverRepository;
import com.diladrive.repository.NotificationRepository;
import com.diladrive.repository.VehicleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AssignmentService {

    private static final List<String> TERMINAL_STATUSES =
            List.of("COMPLETED", "CANCELLED");
    private static final Set<String> TRIP_STATUSES =
            Set.of("ASSIGNED", "OTW", "WAITING", "PICKUP", "DROP", "COMPLETED", "CANCELLED");

    private final AssignmentRepository assignmentRepository;
    private final BookingRepository bookingRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final NotificationRepository notificationRepository;

    public AssignmentService(
            AssignmentRepository assignmentRepository,
            BookingRepository bookingRepository,
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            NotificationRepository notificationRepository) {
        this.assignmentRepository = assignmentRepository;
        this.bookingRepository = bookingRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAllAssignments() {
        return assignmentRepository.findAllByOrderByAssignedAtDesc().stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    @Transactional
    public AssignmentResponse createAssignment(AssignmentRequest request) {
        Booking booking = bookingRepository.findByIdForUpdate(request.bookingId())
                .orElseThrow(() -> notFound("Booking not found."));
        if (!"CONFIRMED".equalsIgnoreCase(booking.getBookingStatus())) {
            throw conflict("Only confirmed bookings can be assigned.");
        }
        if (assignmentRepository.existsByBookingBookingIdAndAssignmentStatusNotIn(
                booking.getBookingId(), TERMINAL_STATUSES)) {
            throw conflict("This booking already has an active assignment.");
        }

        Driver driver = driverRepository.findByIdForUpdate(request.driverId())
                .orElseThrow(() -> notFound("Driver not found."));
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(request.vehicleId())
                .orElseThrow(() -> notFound("Vehicle not found."));

        validateAvailableDriver(driver);
        validateAvailableVehicle(vehicle, booking);

        driver.setDriverAvailability("UNAVAILABLE");
        vehicle.setVehicleStatus("IN_USE");
        booking.setDriverId(driver.getDriverId());
        booking.setVehicleId(vehicle.getVehicleId());

        Assignment assignment = new Assignment();
        assignment.setBooking(booking);
        assignment.setDriver(driver);
        assignment.setVehicle(vehicle);
        assignment.setAssignmentStatus("ASSIGNED");
        Assignment saved = assignmentRepository.save(assignment);
        createAssignmentNotification(saved, "assigned");
        return AssignmentResponse.from(saved);
    }

    @Transactional
    public AssignmentResponse reassign(
            Long assignmentId,
            AssignmentRequest request) {
        Assignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> notFound("Assignment not found."));
        ensureActive(assignment);

        Booking booking = bookingRepository.findByIdForUpdate(
                        assignment.getBooking().getBookingId())
                .orElseThrow(() -> notFound("Booking not found."));
        Driver oldDriver = assignment.getDriver();
        Vehicle oldVehicle = assignment.getVehicle();

        Driver newDriver = request.driverId() == null
                ? oldDriver
                : driverRepository.findByIdForUpdate(request.driverId())
                .orElseThrow(() -> notFound("Driver not found."));
        Vehicle newVehicle = request.vehicleId() == null
                ? oldVehicle
                : vehicleRepository.findByIdForUpdate(request.vehicleId())
                .orElseThrow(() -> notFound("Vehicle not found."));

        boolean driverChanged = !oldDriver.getDriverId().equals(newDriver.getDriverId());
        boolean vehicleChanged = !oldVehicle.getVehicleId().equals(newVehicle.getVehicleId());
        if (!driverChanged && !vehicleChanged) {
            throw badRequest("Choose a different driver or vehicle to reassign.");
        }

        if (driverChanged) {
            validateAvailableDriver(newDriver);
        }
        if (vehicleChanged) {
            validateAvailableVehicle(newVehicle, booking);
        }

        if (driverChanged) {
            oldDriver = driverRepository.findByIdForUpdate(oldDriver.getDriverId())
                    .orElseThrow(() -> notFound("Previously assigned driver not found."));
            oldDriver.setDriverAvailability("AVAILABLE");
            newDriver.setDriverAvailability("UNAVAILABLE");
            assignment.setDriver(newDriver);
        }
        if (vehicleChanged) {
            oldVehicle = vehicleRepository.findByIdForUpdate(oldVehicle.getVehicleId())
                    .orElseThrow(() -> notFound("Previously assigned vehicle not found."));
            oldVehicle.setVehicleStatus("AVAILABLE");
            newVehicle.setVehicleStatus("IN_USE");
            assignment.setVehicle(newVehicle);
        }

        booking.setDriverId(newDriver.getDriverId());
        booking.setVehicleId(newVehicle.getVehicleId());
        Assignment saved = assignmentRepository.save(assignment);
        createAssignmentNotification(saved, "reassigned");
        return AssignmentResponse.from(saved);
    }

    @Transactional
    public AssignmentResponse updateStatus(Long assignmentId, String requestedStatus) {
        String status = requestedStatus == null
                ? ""
                : requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!TRIP_STATUSES.contains(status)) {
            throw badRequest("Invalid trip status.");
        }

        Assignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> notFound("Assignment not found."));
        String currentStatus = assignment.getAssignmentStatus().toUpperCase(Locale.ROOT);
        if (TERMINAL_STATUSES.contains(currentStatus)) {
            throw conflict("A completed or cancelled trip cannot be updated.");
        }
        if (status.equals(currentStatus)) {
            return AssignmentResponse.from(assignment);
        }
        if (!"CANCELLED".equals(status) && !isNextStatus(currentStatus, status)) {
            throw conflict("Trip status must advance to the next step.");
        }

        assignment.setAssignmentStatus(status);
        if (TERMINAL_STATUSES.contains(status)) {
            Driver driver = driverRepository.findByIdForUpdate(
                            assignment.getDriver().getDriverId())
                    .orElseThrow(() -> notFound("Driver not found."));
            Vehicle vehicle = vehicleRepository.findByIdForUpdate(
                            assignment.getVehicle().getVehicleId())
                    .orElseThrow(() -> notFound("Vehicle not found."));
            driver.setDriverAvailability("AVAILABLE");
            vehicle.setVehicleStatus("AVAILABLE");
            Booking booking = bookingRepository.findByIdForUpdate(
                            assignment.getBooking().getBookingId())
                    .orElseThrow(() -> notFound("Booking not found."));
            booking.setDriverId(null);
            booking.setVehicleId(null);
            booking.setBookingStatus(status);
        }
        return AssignmentResponse.from(assignmentRepository.save(assignment));
    }

    private void validateAvailableDriver(Driver driver) {
        if (!"AVAILABLE".equalsIgnoreCase(driver.getDriverAvailability())) {
            throw conflict("The selected driver is not available.");
        }
    }

    private void validateAvailableVehicle(Vehicle vehicle, Booking booking) {
        if (!"AVAILABLE".equalsIgnoreCase(vehicle.getVehicleStatus())) {
            throw conflict("The selected vehicle is not available.");
        }
        if (vehicle.getVehicleType() != null
                && booking.getBookingType() != null
                && !vehicle.getVehicleType().equalsIgnoreCase(booking.getBookingType())) {
            throw conflict("The selected vehicle does not match the booking type.");
        }
        if (booking.getBookingPassengercount() != null
                && (vehicle.getVehicleCapacity() == null
                || vehicle.getVehicleCapacity() < booking.getBookingPassengercount())) {
            throw conflict("The selected vehicle does not have enough passenger capacity.");
        }
    }

    private void createAssignmentNotification(Assignment assignment, String action) {
        Notification notification = new Notification();
        notification.setDriver(assignment.getDriver());
        notification.setTitle("Assignment Update");
        notification.setNotificationType("ASSIGNMENT");
        notification.setMessage(String.format(
                "Assignment %s for booking #%d: %s, driver %s, vehicle %s (%s).",
                action,
                assignment.getBooking().getBookingId(),
                assignment.getAssignmentId() == null
                        ? "new assignment"
                        : "assignment #" + assignment.getAssignmentId(),
                assignment.getDriver().getDriverName(),
                assignment.getVehicle().getVehiclePlateno(),
                assignment.getBooking().getBookingTripdate()));
        notificationRepository.save(notification);
    }

    private void ensureActive(Assignment assignment) {
        if (TERMINAL_STATUSES.contains(
                assignment.getAssignmentStatus().toUpperCase(Locale.ROOT))) {
            throw conflict("Completed or cancelled assignments cannot be reassigned.");
        }
    }

    private boolean isNextStatus(String current, String requested) {
        return switch (current) {
            case "ASSIGNED" -> "OTW".equals(requested);
            case "OTW" -> "WAITING".equals(requested);
            case "WAITING" -> "PICKUP".equals(requested);
            case "PICKUP" -> "DROP".equals(requested);
            case "DROP" -> "COMPLETED".equals(requested);
            default -> false;
        };
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
