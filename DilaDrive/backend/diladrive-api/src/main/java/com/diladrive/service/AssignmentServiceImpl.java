package com.diladrive.service;

import com.diladrive.dto.AssignmentRequest;
import com.diladrive.dto.AssignmentResponse;
import com.diladrive.entity.Assignment;
import com.diladrive.entity.Booking;
import com.diladrive.entity.Driver;
import com.diladrive.entity.Notification;
import com.diladrive.entity.Vehicle;
import com.diladrive.repository.AssignmentRepository;
import com.diladrive.repository.AssignmentSummaryProjection;
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
public class AssignmentServiceImpl implements AssignmentService {

    private static final List<String> TERMINAL_STATUSES =
            List.of("COMPLETED", "CANCELLED");
    private static final Set<String> TRIP_STATUSES =
            Set.of("ASSIGNED", "OTW", "WAITING", "PICKUP", "DROP", "COMPLETED", "CANCELLED");

    private final AssignmentRepository assignmentRepository;
    private final BookingRepository bookingRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final NotificationRepository notificationRepository;

    public AssignmentServiceImpl(
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
        return assignmentRepository.findAllAssignmentSummaries().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> getDriverAssignments(Long driverId) {
        return assignmentRepository.findAssignmentSummariesByDriverId(driverId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getAssignment(Long assignmentId) {
        return assignmentRepository.findAssignmentSummaryById(assignmentId)
                .map(this::toResponse)
                .orElseThrow(() -> notFound("Assignment not found."));
    }

    @Transactional
    public AssignmentResponse createAssignment(AssignmentRequest request) {
        if (request == null
                || request.bookingId() == null
                || request.driverId() == null
                || request.vehicleId() == null) {
            throw badRequest("Booking, driver, and vehicle are required.");
        }
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
        vehicle.setVehicleStatus("UNAVAILABLE");
        booking.setDriverId(driver.getDriverId());
        booking.setVehicleId(vehicle.getVehicleId());
        booking.setBookingStatus("ASSIGNED");

        Assignment assignment = new Assignment();
        assignment.setBooking(booking);
        assignment.setDriver(driver);
        assignment.setVehicle(vehicle);
        assignment.setAssignmentStatus("ASSIGNED");
        Assignment saved = assignmentRepository.save(assignment);
        createAssignmentNotification(saved, "assigned");
        createCustomerNotification(
                booking,
                "Driver assigned",
                "A driver has been assigned to booking "
                        + bookingReference(booking.getBookingId()) + ".",
                "DRIVER_ASSIGNED");
        return findResponse(saved.getAssignmentId());
    }

    @Transactional
    public AssignmentResponse reassign(
            Long assignmentId,
            AssignmentRequest request) {
        if (request == null) {
            throw badRequest("Choose a new driver or vehicle to reassign.");
        }
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
            newVehicle.setVehicleStatus("UNAVAILABLE");
            assignment.setVehicle(newVehicle);
        }

        booking.setDriverId(newDriver.getDriverId());
        booking.setVehicleId(newVehicle.getVehicleId());
        Assignment saved = assignmentRepository.save(assignment);
        createAssignmentNotification(saved, "reassigned");
        createCustomerNotification(
                booking,
                "Driver assignment updated",
                "The driver or vehicle for booking "
                        + bookingReference(booking.getBookingId()) + " has changed.",
                "DRIVER_ASSIGNED");
        return findResponse(saved.getAssignmentId());
    }

    @Transactional
    public AssignmentResponse updateStatus(Long assignmentId, String requestedStatus) {
        String status = normalizeRequestedStatus(requestedStatus);
        Assignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> notFound("Assignment not found."));
        return applyStatusUpdate(assignment, status);
    }

    @Transactional
    public AssignmentResponse updateDriverStatus(
            Long assignmentId, Long driverId, String requestedStatus) {
        String status = normalizeRequestedStatus(requestedStatus);
        if ("CANCELLED".equals(status)) {
            throw badRequest("Drivers can only advance a trip through its status steps.");
        }
        Assignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> notFound("Assignment not found."));
        if (assignment.getDriver() == null
                || !driverId.equals(assignment.getDriver().getDriverId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Assignment not found.");
        }
        return applyStatusUpdate(assignment, status);
    }

    private AssignmentResponse applyStatusUpdate(Assignment assignment, String status) {
        Long assignmentId = assignment.getAssignmentId();
        if (!TRIP_STATUSES.contains(status)) {
            throw badRequest("Invalid trip status.");
        }

        String currentStatus = normalizeStatus(assignment.getAssignmentStatus());
        if (TERMINAL_STATUSES.contains(currentStatus)) {
            throw conflict("A completed or cancelled trip cannot be updated.");
        }
        if (status.equals(currentStatus)) {
            return findResponse(assignmentId);
        }
        if (!"CANCELLED".equals(status) && !isNextStatus(currentStatus, status)) {
            throw conflict("Trip status must advance to the next step.");
        }

        Booking booking = bookingRepository.findByIdForUpdate(
                        assignment.getBooking().getBookingId())
                .orElseThrow(() -> notFound("Booking not found."));
        assignment.setAssignmentStatus(status);
        if (TERMINAL_STATUSES.contains(status)) {
            releaseResources(assignment);
            clearBookingResources(booking);
        }
        booking.setBookingStatus(status);
        assignmentRepository.save(assignment);
        createCustomerNotification(
                booking,
                status.equals("CANCELLED") ? "Trip cancelled" : "Trip status updated",
                "Your booking " + bookingReference(booking.getBookingId())
                        + " is now " + statusLabel(status) + ".",
                "TRIP_STATUS");
        return findResponse(assignmentId);
    }

    private String normalizeRequestedStatus(String requestedStatus) {
        String status = requestedStatus == null
                ? ""
                : requestedStatus.trim().toUpperCase(Locale.ROOT);
        return status;
    }

    @Transactional
    public AssignmentResponse completeAssignment(Long assignmentId) {
        Assignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> notFound("Assignment not found."));
        ensureActive(assignment);
        Booking booking = bookingRepository.findByIdForUpdate(
                        assignment.getBooking().getBookingId())
                .orElseThrow(() -> notFound("Booking not found."));
        assignment.setAssignmentStatus("COMPLETED");
        releaseResources(assignment);
        clearBookingResources(booking);
        booking.setBookingStatus("COMPLETED");
        assignmentRepository.save(assignment);
        createCustomerNotification(
                booking,
                "Trip completed",
                "Your booking " + bookingReference(booking.getBookingId())
                        + " has been completed.",
                "TRIP_STATUS");
        return findResponse(assignmentId);
    }

    @Transactional
    public void deleteAssignment(Long assignmentId) {
        Assignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> notFound("Assignment not found."));
        ensureActive(assignment);
        Booking booking = bookingRepository.findByIdForUpdate(
                        assignment.getBooking().getBookingId())
                .orElseThrow(() -> notFound("Booking not found."));
        assignment.setAssignmentStatus("CANCELLED");
        releaseResources(assignment);
        clearBookingResources(booking);
        booking.setBookingStatus("CANCELLED");
        assignmentRepository.save(assignment);
        createCustomerNotification(
                booking,
                "Trip cancelled",
                "The assignment for booking "
                        + bookingReference(booking.getBookingId()) + " was cancelled.",
                "TRIP_STATUS");
    }

    private void validateAvailableDriver(Driver driver) {
        if (!"ACTIVE".equalsIgnoreCase(driver.getDriverStatus())) {
            throw conflict("The selected driver account is not active.");
        }
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
        if (booking.getBookingLuggagecount() != null
                && booking.getBookingLuggagecount() > 0
                && (vehicle.getVehicleLuggageCapacity() == null
                || vehicle.getVehicleLuggageCapacity() < booking.getBookingLuggagecount())) {
            throw conflict("The selected vehicle does not have enough luggage capacity.");
        }
        if (booking.getBookingTripdate() != null
                && vehicle.getVehicleRoadtaxexpiry() != null
                && vehicle.getVehicleRoadtaxexpiry().isBefore(booking.getBookingTripdate())) {
            throw conflict("The selected vehicle's road tax expires before the trip date.");
        }
        if (booking.getBookingTripdate() != null
                && vehicle.getVehicleServiceexpiry() != null
                && vehicle.getVehicleServiceexpiry().isBefore(booking.getBookingTripdate())) {
            throw conflict("The selected vehicle's service certification expires before the trip date.");
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

    private void createCustomerNotification(
            Booking booking,
            String title,
            String message,
            String type) {
        if (booking.getCustId() == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientRole("CUSTOMER");
        notification.setRecipientId(booking.getCustId());
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNotificationType(type);
        notificationRepository.save(notification);
    }

    private String bookingReference(Long bookingId) {
        return "BK-" + String.format("%06d", bookingId);
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "OTW" -> "on the way";
            case "WAITING" -> "waiting for pickup";
            case "PICKUP" -> "passenger picked up";
            case "DROP" -> "drop-off in progress";
            case "COMPLETED" -> "completed";
            case "CANCELLED" -> "cancelled";
            default -> status.toLowerCase(Locale.ROOT);
        };
    }

    private void releaseResources(Assignment assignment) {
        Driver driver = driverRepository.findByIdForUpdate(
                        assignment.getDriver().getDriverId())
                .orElseThrow(() -> notFound("Driver not found."));
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(
                        assignment.getVehicle().getVehicleId())
                .orElseThrow(() -> notFound("Vehicle not found."));
        driver.setDriverAvailability("AVAILABLE");
        vehicle.setVehicleStatus("AVAILABLE");
    }

    private void clearBookingResources(Booking booking) {
        booking.setDriverId(null);
        booking.setVehicleId(null);
    }

    private AssignmentResponse findResponse(Long assignmentId) {
        assignmentRepository.flush();
        return assignmentRepository.findAssignmentSummaryById(assignmentId)
                .map(this::toResponse)
                .orElseThrow(() -> notFound("Assignment not found."));
    }

    private AssignmentResponse toResponse(AssignmentSummaryProjection summary) {
        return new AssignmentResponse(
                summary.getAssignmentId(),
                summary.getBookingId(),
                summary.getCustomer(),
                summary.getTripDate(),
                summary.getPickupTime(),
                summary.getTripType(),
                summary.getFlightNo(),
                summary.getPickupAddress(),
                summary.getDestinationAddress(),
                summary.getPassengerCount(),
                summary.getLuggageCount(),
                summary.getLuggageSize(),
                summary.getDriverId(),
                summary.getDriverName(),
                summary.getVehicleId(),
                summary.getVehiclePlate(),
                summary.getAssignmentStatus(),
                summary.getAssignmentDate());
    }

    private void ensureActive(Assignment assignment) {
        if (TERMINAL_STATUSES.contains(normalizeStatus(assignment.getAssignmentStatus()))) {
            throw conflict("Completed or cancelled assignments cannot be reassigned.");
        }
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
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
