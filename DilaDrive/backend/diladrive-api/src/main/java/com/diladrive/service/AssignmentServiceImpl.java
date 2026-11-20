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
    @org.springframework.beans.factory.annotation.Autowired
    private TripFinanceService tripFinance;

    private static final List<String> TERMINAL_STATUSES =
            List.of("COMPLETED", "CANCELLED", "SUPERSEDED");
    private static final Set<String> TRIP_STATUSES =
            Set.of("ASSIGNED", "OTW", "WAITING", "PICKUP", "DROP", "COMPLETED");

    @org.springframework.beans.factory.annotation.Autowired
    private CustomerTripService customerTrips;
    @org.springframework.beans.factory.annotation.Autowired
    private BookingPushService bookingPush;
    @org.springframework.beans.factory.annotation.Autowired
    private BookingPriceService bookingPrices;

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
    public AssignmentResponse confirmAndAssign(AssignmentRequest request, java.math.BigDecimal tripFare) {
        return saveInitialAssignment(request, true, tripFare);
    }

    @Transactional
    public AssignmentResponse createAssignment(AssignmentRequest request) {
        return saveInitialAssignment(request, false, null);
    }

    private AssignmentResponse saveInitialAssignment(AssignmentRequest request,
            boolean confirm, java.math.BigDecimal tripFare) {
        if (request == null
                || request.bookingId() == null
                || request.driverId() == null) {
            throw badRequest("Booking and driver are required. The driver must have a linked vehicle.");
        }
        Booking booking = bookingRepository.findByIdForUpdate(request.bookingId())
                .orElseThrow(() -> notFound("Booking not found."));
        String bookingStatus = String.valueOf(booking.getBookingStatus()).toUpperCase(Locale.ROOT);
        if (booking.isRoadDistancePending()) {
            throw conflict("Confirm the measured road distance before assigning this booking.");
        }
        if (confirm && !Set.of("PENDING", "PENDING_VERIFICATION").contains(bookingStatus)) {
            throw conflict("Only a pending booking can be confirmed. Refresh its current status.");
        }
        java.math.BigDecimal finalFare = tripFare != null ? tripFare : booking.getBookingTripfare();
        if (confirm && (finalFare == null || finalFare.signum() < 0 || (finalFare.signum()==0 && booking.getFareSnapshot()==null))) {
            throw badRequest("Enter a final fare greater than zero.");
        }
        if (!confirm && !"CONFIRMED".equalsIgnoreCase(booking.getBookingStatus())) {
            throw conflict("Only confirmed bookings can be assigned.");
        }
        if (assignmentRepository.existsByBookingBookingIdAndAssignmentStatusNotIn(
                booking.getBookingId(), TERMINAL_STATUSES)) {
            throw conflict("This booking already has an active assignment.");
        }

        Driver driver = driverRepository.findByIdForUpdate(request.driverId())
                .orElseThrow(() -> notFound("Driver not found."));
        Vehicle vehicle = linkedVehicle(driver, request.vehicleId());
        validateAvailableDriver(driver);
        validateAvailableVehicle(vehicle, booking);
        if(bookingPrices!=null) bookingPrices.requireAcceptedCategory(booking,vehicle.getVehicleType(),tripFare);
        java.time.LocalDateTime endAt = request.scheduledEndAt() != null ? request.scheduledEndAt() : booking.getReservedEndAt();
        validateSchedule(booking, endAt, driver, vehicle, null);

        booking.setDriverId(driver.getDriverId());
        booking.setVehicleId(vehicle.getVehicleId());
        booking.setBookingStatus("CONFIRMED");
        if (confirm) booking.setBookingTripfare(finalFare);

        Assignment assignment = new Assignment();
        assignment.setBooking(booking);
        assignment.setDriver(driver);
        assignment.setVehicle(vehicle);
        assignment.setScheduledEndAt(endAt);
        assignment.setAssignmentStatus("ASSIGNED");
        Assignment saved = assignmentRepository.save(assignment);
        createAssignmentNotification(saved, "assigned");
        if (confirm) {
            createCustomerNotification(booking, "Booking confirmed",
                    "Your booking " + bookingReference(booking.getBookingId())
                            + " has been confirmed with a driver and vehicle.", "BOOKING_APPROVED");
        } else {
            createCustomerNotification(booking, "Driver assigned",
                    "A driver has been assigned to booking " + bookingReference(booking.getBookingId()) + ".",
                    "DRIVER_ASSIGNED");
        }
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

        if (request.driverId() == null || request.driverId().equals(oldDriver.getDriverId())) {
            throw badRequest("Select another eligible driver; their linked vehicle is selected automatically.");
        }
        Driver newDriver = driverRepository.findByIdForUpdate(request.driverId())
                .orElseThrow(() -> notFound("Driver not found."));
        Vehicle newVehicle = linkedVehicle(newDriver, request.vehicleId());
        validateAvailableDriver(newDriver);
        validateAvailableVehicle(newVehicle, booking);
        if(bookingPrices!=null) bookingPrices.requireAcceptedCategory(booking,newVehicle.getVehicleType(),null);
        java.time.LocalDateTime endAt = request.scheduledEndAt() != null ? request.scheduledEndAt() : assignment.getScheduledEndAt();
        validateSchedule(booking, endAt, newDriver, newVehicle, assignmentId);
        // Preserve the original driver/vehicle and times as immutable history.
        assignment.setAssignmentStatus("SUPERSEDED");
        assignmentRepository.saveAndFlush(assignment);
        Assignment replacement = new Assignment();
        replacement.setBooking(booking);
        replacement.setDriver(newDriver);
        replacement.setVehicle(newVehicle);
        replacement.setScheduledEndAt(endAt);
        replacement.setAssignmentStatus("ASSIGNED");

        booking.setDriverId(newDriver.getDriverId());
        booking.setVehicleId(newVehicle.getVehicleId());
        booking.setBookingStatus("CONFIRMED");
        Assignment saved = assignmentRepository.save(replacement);
        Notification previousDriverNotice=new Notification();
        previousDriverNotice.setDriver(oldDriver); previousDriverNotice.setTitle("Assignment changed");
        previousDriverNotice.setNotificationType("ASSIGNMENT");
        previousDriverNotice.setMessage("Your previous assignment for " + bookingReference(booking.getBookingId()) + " has been superseded.");
        notificationRepository.save(previousDriverNotice);
        if (bookingPush != null) bookingPush.notifyAfterCommit("DRIVER",oldDriver.getDriverId(),
                "Assignment changed", "Your previous assignment for " + bookingReference(booking.getBookingId()) + " has been superseded.");
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
        if (!isNextStatus(currentStatus, status)) {
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
        // Progress belongs to the trip, while its booking stays CONFIRMED.
        if (TERMINAL_STATUSES.contains(status)) booking.setBookingStatus(status);
        if ("COMPLETED".equals(status) && tripFinance != null) {
            tripFinance.recordCompletion(assignment, booking);
        }
        assignmentRepository.save(assignment);
        createCustomerNotification(
                booking,
                "Trip status updated",
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
        if (tripFinance != null) tripFinance.recordCompletion(assignment, booking);
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

    public record AssignmentOption(Long vehicleId, String vehicleType, String vehicleModel, String vehiclePlate,
            Integer passengerCapacity, Integer luggagePointsCapacity, Long driverId, String driverName) { }

    /** A preview only; confirmation repeats every check under resource row locks. */
    @Transactional(readOnly=true)
    public List<AssignmentOption> options(Long bookingId, java.time.LocalDateTime requestedEnd) {
        Booking booking=bookingRepository.findById(bookingId).orElseThrow(() -> notFound("Booking not found."));
        if (!Set.of("PENDING","PENDING_VERIFICATION","CONFIRMED","ASSIGNED","OTW","WAITING","PICKUP","DROP").contains(normalizeStatus(booking.getBookingStatus())))
            throw conflict("Only pending or confirmed bookings can be scheduled.");
        var end=requestedEnd != null ? requestedEnd : booking.getReservedEndAt();
        if (booking.getBookingTripdate()==null || booking.getBookingPickuptime()==null || end==null
                || !end.isAfter(booking.getBookingTripdate().atTime(booking.getBookingPickuptime())))
            throw badRequest("Enter a valid scheduled end time to recheck availability.");
        if (booking.getReservedEndAt()!=null && end.isBefore(booking.getReservedEndAt()))
            throw badRequest("Scheduled end must include the road travel duration and repositioning buffer.");
        var result=new java.util.ArrayList<AssignmentOption>();
        for (Vehicle vehicle:vehicleRepository.findAll()) {
            if (vehicle.getDriverId()==null) continue;
            Driver driver=driverRepository.findById(vehicle.getDriverId()).orElse(null);
            if (driver==null) continue;
            if (vehicleRepository.findByDriverId(driver.getDriverId()).size()!=1) continue;
            try {
                validateAvailableDriver(driver);
                validateAvailableVehicle(vehicle,booking);
                validateSchedule(booking,end,driver,vehicle,null);
                result.add(new AssignmentOption(vehicle.getVehicleId(),vehicle.getVehicleType(),vehicle.getVehicleModel(),
                        vehicle.getVehiclePlateno(),vehicle.passengerLimit(),vehicle.getVehicleLuggageCapacity(),
                        driver.getDriverId(),driver.getDriverName()));
            } catch(ResponseStatusException unavailable) {
                // Omit resources that fail the same rules used at confirmation.
            }
        }
        result.sort(java.util.Comparator.comparing(AssignmentOption::passengerCapacity)
                .thenComparing(AssignmentOption::luggagePointsCapacity,java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparing(AssignmentOption::vehicleId));
        return result;
    }

    private Vehicle linkedVehicle(Driver driver, Long requestedVehicleId) {
        List<Vehicle> linked = vehicleRepository.findByDriverId(driver.getDriverId());
        if (linked.size() != 1) {
            throw conflict("Driver must have exactly one registered vehicle. Complete vehicle registration in Driver Management.");
        }
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(linked.get(0).getVehicleId())
                .orElseThrow(() -> notFound("Linked vehicle not found."));
        if (!driver.getDriverId().equals(vehicle.getDriverId())) throw conflict("Vehicle relationship changed. Refresh and try again.");
        if (requestedVehicleId != null && !requestedVehicleId.equals(vehicle.getVehicleId())) {
            throw badRequest("The vehicle must be the selected driver's current linked vehicle.");
        }
        return vehicle;
    }

    private void validateSchedule(Booking booking, java.time.LocalDateTime endAt,
            Driver driver, Vehicle vehicle, Long excludedAssignmentId) {
        if (booking.getBookingTripdate() == null || booking.getBookingPickuptime() == null) {
            throw badRequest("A booking pickup date and time are required for scheduling.");
        }
        java.time.LocalDateTime startAt = booking.getBookingTripdate().atTime(booking.getBookingPickuptime());
        if (endAt == null || !endAt.isAfter(startAt)) {
            throw badRequest("Enter a scheduled end time after the booking pickup time.");
        }
        if (booking.getReservedEndAt() != null && endAt.isBefore(booking.getReservedEndAt())) {
            throw badRequest("Scheduled end must include the road travel duration and repositioning buffer.");
        }
        if (customerTrips != null && customerTrips.hasConflict(vehicle.getVehicleId(), driver.getDriverId(), startAt, endAt, booking.getBookingId())) {
            throw conflict("This driver or vehicle is no longer available for the selected time. Choose another suitable vehicle.");
        }
        for (Assignment reserved : assignmentRepository.findResourceReservations(driver.getDriverId(), vehicle.getVehicleId())) {
            if (reserved.getAssignmentId().equals(excludedAssignmentId)) continue;
            Booking other = reserved.getBooking();
            // Unknown legacy schedules cannot be assumed safe to overlap.
            if (other.getBookingTripdate() == null || other.getBookingPickuptime() == null) {
                throw conflict("A legacy active assignment has an unknown schedule. Complete or cancel it before reuse.");
            }
            java.time.LocalDateTime otherStart = other.getBookingTripdate().atTime(other.getBookingPickuptime());
            java.time.LocalDateTime otherEnd = reserved.getScheduledEndAt();
            if (startAt.isBefore(otherEnd == null ? java.time.LocalDateTime.MAX : otherEnd) && otherStart.isBefore(endAt)) {
                throw conflict("The linked driver and vehicle have an overlapping booking assignment.");
            }
        }
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
        if (!Set.of("AVAILABLE", "ACTIVE").contains(
                String.valueOf(vehicle.getVehicleStatus()).toUpperCase(Locale.ROOT))) {
            throw conflict("The selected vehicle is not available.");
        }
        // bookingType is a recommendation, not a restriction on Admin alternatives.
        if (booking.getBookingPassengercount() != null
                && vehicle.passengerLimit() < booking.getBookingPassengercount()) {
            throw conflict("The selected vehicle does not have enough passenger capacity.");
        }
        if (booking.getBookingLuggagecount() != null
                && booking.getBookingLuggagecount() > 0
                && (vehicle.getVehicleLuggageCapacity() == null
                || vehicle.getVehicleLuggageCapacity() < (booking.getLuggageUnits() != null ? booking.getLuggageUnits() : booking.getBookingLuggagecount()))) {
            throw conflict("The selected vehicle does not have enough luggage capacity.");
        }
        if (!vehicle.fitsSeparateLuggage(booking.getLargeCount() == null ? 0 : booking.getLargeCount(),
                booking.getSmallCount() == null ? 0 : booking.getSmallCount())) {
            throw conflict("The selected vehicle does not have enough large luggage or backpack capacity.");
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
        if (bookingPush != null) bookingPush.notifyAfterCommit("DRIVER",assignment.getDriver().getDriverId(),
                notification.getTitle(),notification.getMessage());
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
        if (bookingPush != null) bookingPush.notifyAfterCommit("CUSTOMER",booking.getCustId(),title,message);
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
        // Reservation release leaves administrator-managed availability unchanged.
        // Operational status is independent of booking reservations.
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
                summary.getAssignmentDate(), summary.getScheduledEndAt(),
                summary.getSmallCount(),summary.getMediumCount(),summary.getLargeCount(),summary.getLuggageUnits());
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
