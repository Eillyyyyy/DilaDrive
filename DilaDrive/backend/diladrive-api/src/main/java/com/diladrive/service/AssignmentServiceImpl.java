package com.diladrive.service;

import com.diladrive.dto.NotificationTarget;

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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AssignmentServiceImpl implements AssignmentService {
    @org.springframework.beans.factory.annotation.Autowired
    private TripFinanceService tripFinance;

    @org.springframework.beans.factory.annotation.Value("${booking.buffer-minutes:30}")
    private int bufferMinutes;

    private static final List<String> TERMINAL_STATUSES =
            List.of("COMPLETED", "CANCELLED", "SUPERSEDED");
    private static final Set<String> TRIP_STATUSES =
            Set.of("ASSIGNED", "OTW", "WAITING", "PICKUP", "DROP", "COMPLETED");

    @org.springframework.beans.factory.annotation.Autowired
    private CustomerTripService customerTrips;
    @org.springframework.beans.factory.annotation.Autowired private TripMonitoringService tripMonitoring;
    @org.springframework.beans.factory.annotation.Autowired
    private BookingPushService bookingPush;
    @org.springframework.beans.factory.annotation.Autowired private NotificationService notifications;
    @org.springframework.beans.factory.annotation.Autowired
    private BookingPriceService bookingPrices;

    private final AssignmentRepository assignmentRepository;
    private final BookingRepository bookingRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final NotificationRepository notificationRepository;

    @org.springframework.beans.factory.annotation.Value("${booking.reassignment-cutoff-minutes:120}")
    private long reassignmentCutoffMinutes = 120;
    @org.springframework.beans.factory.annotation.Value("${booking.allow-reassignment-upgrades:false}")
    private boolean allowReassignmentUpgrades;

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
    public AssignmentResponse getDriverAssignment(Long assignmentId, Long driverId) {
        return assignmentRepository.findDriverAssignmentDetail(assignmentId, driverId)
                .map(this::toResponse)
                .orElseThrow(() -> notFound("Assignment not found."));
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getAssignment(Long assignmentId) {
        return assignmentRepository.findAssignmentSummaryById(assignmentId)
                .map(this::toResponse)
                .orElseThrow(() -> notFound("Assignment not found."));
    }

    @Transactional
    public AssignmentResponse confirmAndAssign(AssignmentRequest request, java.math.BigDecimal tripFare) {
        return saveInitialAssignment(request, true);
    }

    @Transactional
    public AssignmentResponse createAssignment(AssignmentRequest request) {
        return saveInitialAssignment(request, false);
    }

    @org.springframework.beans.factory.annotation.Autowired private PricingService pricing;

    private AssignmentResponse saveInitialAssignment(AssignmentRequest request,
            boolean confirm) {
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
        var reservation = confirm && customerTrips != null ? customerTrips.reservation(booking.getBookingId()) : null;
        if (reservation != null && reservation.expired()) {
            throw conflict("The reservation expired. Change Assignment to validate and reserve a pair again.");
        }
        java.math.BigDecimal finalFare = booking.getBookingTripfare();
        if (confirm && finalFare == null && booking.getBookingDistancekm() != null
                && booking.getBookingDistancekm() > 0 && booking.getBookingType() != null) {
            pricing.lock(booking.getBookingType());
            finalFare = pricing.calculate(booking.getBookingType(), booking.getBookingDistancekm()).totalFare();
        }
        if (confirm && finalFare == null) {
            throw conflict("Confirm road distance before assigning this booking.");
        }
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
        java.time.LocalDateTime endAt = request.scheduledEndAt() != null ? request.scheduledEndAt()
                : reservation == null ? minimumEnd(booking) : reservation.end();
        validateAvailableDriver(driver, endAt);
        validateAvailableVehicle(vehicle, booking, endAt);
        if(bookingPrices!=null) bookingPrices.requireAcceptedCategory(booking,vehicle.getVehicleType(),null);
        if (reservation != null && (!reservation.driverId().equals(request.driverId())
                || (request.vehicleId() != null && !reservation.vehicleId().equals(request.vehicleId())))) {
            throw conflict("Save the replacement with Change Assignment before confirming.");
        }

        validateSchedule(booking, endAt, driver, vehicle, null);
        if (reservation != null && (reservation.expired() || !reservation.vehicleId().equals(vehicle.getVehicleId()))) {
            throw conflict("The hold expired or the pair changed. Reserve an eligible pair again.");
        }

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
        assignmentRepository.flush();
        recordMilestone(saved.getAssignmentId(), "ASSIGNED");
        if (customerTrips != null) customerTrips.releaseReservation(booking.getBookingId());
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
        if (!Set.of("CONFIRMED", "ASSIGNED", "OTW", "WAITING", "PICKUP", "DROP")
                .contains(normalizeStatus(booking.getBookingStatus()))) {
            throw conflict("Only active confirmed trips can be reassigned.");
        }
        String phase = normalizeStatus(assignment.getAssignmentStatus());
        if (!"ASSIGNED".equals(phase)) {
            throw conflict("This trip has started. Use the incident workflow for an active trip.");
        }
        if (request.reason() == null || request.reason().isBlank() || request.reason().length() > 500) {
            throw badRequest("Enter the reason for reassignment (maximum 500 characters).");
        }
        if (booking.getBookingTripdate() == null || booking.getBookingPickuptime() == null) {
            throw conflict("Record the pickup schedule before reassigning this trip.");
        }
        var pickup = booking.getBookingTripdate().atTime(booking.getBookingPickuptime());
        var now = malaysiaNow();
        if (!pickup.isAfter(now)) throw conflict("Reassignment is only available before pickup.");
        if (!pickup.isAfter(now.plusMinutes(reassignmentCutoffMinutes)) && !request.emergency()) {
            throw conflict("The reassignment cutoff has passed. Select emergency reassignment and provide a reason.");
        }

        if (request.driverId() == null) {
            throw badRequest("Select another eligible driver; their linked vehicle is selected automatically.");
        }
        Driver newDriver = driverRepository.findByIdForUpdate(request.driverId())
                .orElseThrow(() -> notFound("Driver not found."));
        Vehicle newVehicle = linkedVehicle(newDriver, request.vehicleId());
        if (newDriver.getDriverId().equals(oldDriver.getDriverId())
                && newVehicle.getVehicleId().equals(assignment.getVehicle().getVehicleId())) {
            throw badRequest("Choose a different driver or vehicle.");
        }
        java.time.LocalDateTime endAt = request.scheduledEndAt() != null ? request.scheduledEndAt() : assignment.getScheduledEndAt();
        validateAvailableDriver(newDriver, endAt);
        validateAvailableVehicle(newVehicle, booking, endAt);
        if (!replacementCategoryAllowed(booking, newVehicle)
                || (!sameCategory(booking.getBookingType(), newVehicle.getVehicleType()) && allowReassignmentUpgrades
                    && preview(booking.getBookingId(), request.scheduledEndAt(), assignmentId).alternatives().stream()
                            .anyMatch(option -> sameCategory(option.vehicleType(), booking.getBookingType())))) {
            throw conflict("Select a replacement in the confirmed vehicle category.");
        }
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
        replacement.setPreviousAssignmentId(assignmentId);
        replacement.setReassignmentReason(request.reason().strip());
        replacement.setAssignmentDate(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kuala_Lumpur")));

        booking.setDriverId(newDriver.getDriverId());
        booking.setVehicleId(newVehicle.getVehicleId());
        booking.setBookingStatus("CONFIRMED");
        Assignment saved = assignmentRepository.save(replacement);
        assignmentRepository.flush();
        recordMilestone(assignmentId, "SUPERSEDED");
        recordMilestone(saved.getAssignmentId(), "ASSIGNED");
        Notification previousDriverNotice=new Notification();
        previousDriverNotice.setDriver(oldDriver); previousDriverNotice.setTitle("Assignment changed");
        previousDriverNotice.setNotificationType("ASSIGNMENT");
        previousDriverNotice.setMessage("Your previous assignment for " + bookingReference(booking.getBookingId()) + " has been superseded.");
        notificationRepository.save(previousDriverNotice);
        if (bookingPush != null) bookingPush.notifyAfterCommit("DRIVER",oldDriver.getDriverId(),
                "Assignment changed", "Your previous assignment for " + bookingReference(booking.getBookingId()) + " has been superseded.");
        createAssignmentNotification(saved, "reassigned");
        if (notifications != null) {
            notifications.notifyAdmins("Booking reassigned", bookingReference(booking.getBookingId())
                    + " was reassigned.", "ASSIGNMENT_REASSIGNED", NotificationTarget.ASSIGNMENT,
                    saved.getAssignmentId(), "assignment-reassigned:" + saved.getAssignmentId());
        }
        createCustomerNotification(
                booking,
                "Driver assignment updated",
                "The driver or vehicle for booking "
                        + bookingReference(booking.getBookingId()) + " has changed.",
                "DRIVER_ASSIGNED");
        return findResponse(saved.getAssignmentId());
    }


    private LocalDateTime malaysiaNow() {
        return LocalDateTime.now(java.time.ZoneId.of("Asia/Kuala_Lumpur"));
    }

    private boolean replacementCategoryAllowed(Booking booking, Vehicle vehicle) {
        String original = booking.getBookingType();
        if (original == null || original.isBlank() || sameCategory(original, vehicle.getVehicleType())) return true;
        int originalRank = Vehicle.scheduledCategoryRank(original);
        return allowReassignmentUpgrades && originalRank > 0
                && Vehicle.scheduledCategoryRank(vehicle.getVehicleType()) > originalRank;
    }

    @Transactional
    public AssignmentPreview changeSuggestedAssignment(AssignmentRequest request) {
        Booking booking = bookingRepository.findByIdForUpdate(request.bookingId())
                .orElseThrow(() -> notFound("Booking not found."));
        if (!Set.of("PENDING", "PENDING_VERIFICATION").contains(normalizeStatus(booking.getBookingStatus()))
                || booking.isRoadDistancePending()) {
            throw conflict("Only a pending booking with a confirmed route can reserve an assignment.");
        }
        if (request.driverId() == null) throw badRequest("Choose an eligible driver and vehicle.");
        Driver driver = driverRepository.findByIdForUpdate(request.driverId()).orElseThrow(() -> notFound("Driver not found."));
        Vehicle vehicle = linkedVehicle(driver, request.vehicleId());
        if (!sameCategory(booking.getBookingType(), vehicle.getVehicleType())) {
            throw conflict("Choose a pair in the customer's selected vehicle category.");
        }
        var end = request.scheduledEndAt() == null ? minimumEnd(booking) : request.scheduledEndAt();
        validateAvailableDriver(driver, end);
        validateAvailableVehicle(vehicle, booking, end);
        if (vehicle.getBootCapacityUnits() == null || vehicle.getBootCapacityUnits() < 0) {
            throw conflict("Configure the vehicle luggage capacity before reserving this pair.");
        }
        validateSchedule(booking, end, driver, vehicle, null);
        booking.setPreferredVehicleId(vehicle.getVehicleId());
        bookingRepository.saveAndFlush(booking);
        customerTrips.reserve(booking, vehicle.getVehicleId(), driver.getDriverId(), end);
        return preview(booking.getBookingId(), end, null);
    }

    @Transactional
    public AssignmentResponse updateStatus(Long assignmentId, String requestedStatus) {
        String status = normalizeRequestedStatus(requestedStatus);
        Assignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> notFound("Assignment not found."));
        return applyStatusUpdate(assignment, status, false);
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
        return applyStatusUpdate(assignment, status, true);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.diladrive.repository.VerificationPhotoRepository verificationPhotos;

    private AssignmentResponse applyStatusUpdate(Assignment assignment, String status, boolean driverUpdate) {
        Long assignmentId = assignment.getAssignmentId();
        if (!TRIP_STATUSES.contains(status)) {
            throw badRequest("Invalid trip status.");
        }

        String currentStatus = normalizeStatus(assignment.getAssignmentStatus());
        if (status.equals(currentStatus)) {
            return findResponse(assignmentId);
        }
        if (TERMINAL_STATUSES.contains(currentStatus)) {
            throw conflict("A completed or cancelled trip cannot be updated.");
        }
        if (!isNextStatus(currentStatus, status)) {
            throw conflict("Trip status must advance to the next step.");
        }

        Booking booking = bookingRepository.findByIdForUpdate(
                        assignment.getBooking().getBookingId())
                .orElseThrow(() -> notFound("Booking not found."));
        if (driverUpdate && !"CONFIRMED".equalsIgnoreCase(booking.getBookingStatus())
                && !currentStatus.equalsIgnoreCase(booking.getBookingStatus())) {
            throw conflict("Trip updates require an active booking.");
        }
        if (Set.of("PICKUP", "DROP").contains(status)
                && !verificationPhotos.existsByAssignmentAssignmentIdAndPhotoType(assignmentId, status)) {
            throw conflict(status.equals("PICKUP") ? "Upload a pickup photo first." : "Upload a drop-off photo first.");
        }
        assignment.setAssignmentStatus(status);
        recordMilestone(assignmentId, status);
        if (TERMINAL_STATUSES.contains(status)) {
            releaseResources(assignment);
            clearBookingResources(booking);
        }
        // Progress belongs to the trip, while its booking stays CONFIRMED.
        if (TERMINAL_STATUSES.contains(status)) booking.setBookingStatus(status);
        if ("COMPLETED".equals(status) && tripFinance != null && assignment.getDriver() != null
                && booking.getBookingTripfare() != null && booking.getBookingTripfare().signum() >= 0) {
            tripFinance.snapshot(booking);
        }
        assignmentRepository.save(assignment);
        createCustomerNotification(
                booking,
                "Trip status updated",
                "Your booking " + bookingReference(booking.getBookingId())
                        + " is now " + statusLabel(status) + ".",
                "TRIP_STATUS");
        if (notifications != null) {
            String title = switch (status) {
                case "OTW" -> "Trip started";
                case "COMPLETED" -> "Trip completed";
                case "CANCELLED" -> "Booking cancelled";
                default -> "Trip status updated";
            };
            notifications.notifyAdmins(title, bookingReference(booking.getBookingId())
                    + " is now " + statusLabel(status) + ".",
                    "CANCELLED".equals(status) ? "BOOKING_CANCELLED" : "TRIP_STATUS", NotificationTarget.ASSIGNMENT,
                    assignmentId, "trip-status:" + assignmentId + ":" + status);
        }
        return findResponse(assignmentId);
    }

    private void recordMilestone(Long assignmentId, String status) {
        if (tripMonitoring != null) tripMonitoring.record(assignmentId, status);
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
        return applyStatusUpdate(assignment, "COMPLETED", false);
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
        recordMilestone(assignmentId, "CANCELLED");
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
            Integer passengerCapacity, Integer luggagePointsCapacity, Long driverId, String driverName, boolean customerAcceptanceRequired) { }

    public record PreferenceChecks(boolean vehicleAvailable, boolean driverAvailable,
            boolean linkedPair, boolean capacityCompatible, Boolean scheduleConflict,
            boolean eligible, List<String> reasons) { }

    public record AssignmentPreview(Long bookingId, String bookingReference, LocalDateTime pickupAt,
            Long preferredVehicleId, Long driveDurationSeconds, Long operationalBufferSeconds,
            LocalDateTime estimatedTripEnd, PreferenceChecks preferenceChecks,
            AssignmentOption preferred, String preferredCategory,
            String unavailableReason, java.time.LocalDateTime scheduledEndAt, List<AssignmentOption> alternatives,
            String reservationStatus, LocalDateTime reservationExpiresAt, boolean emergencyRequired) { }

    @Transactional(readOnly = true)
    public List<AssignmentOption> options(Long bookingId, java.time.LocalDateTime requestedEnd) {
        return preview(bookingId, requestedEnd, null).alternatives();
    }

    @Transactional(readOnly = true)
    public AssignmentPreview preview(Long bookingId, java.time.LocalDateTime requestedEnd, Long replacementOf) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> notFound("Booking not found."));
        if (!Set.of("PENDING", "PENDING_VERIFICATION", "CONFIRMED", "ASSIGNED", "OTW", "WAITING", "PICKUP", "DROP")
                .contains(normalizeStatus(booking.getBookingStatus()))) {
            throw conflict("Only pending or active confirmed trips can be scheduled.");
        }
        if (booking.isRoadDistancePending()) {
            throw conflict("Confirm the measured road distance before assigning this booking.");
        }
        Assignment current = replacementOf == null ? null : assignmentRepository.findActiveByBookingId(bookingId)
                .filter(assignment -> replacementOf.equals(assignment.getAssignmentId()))
                .orElseThrow(() -> conflict("This assignment has changed. Refresh before reassigning."));
        var reservation = current == null && customerTrips != null ? customerTrips.reservation(bookingId) : null;
        var estimatedEnd = minimumEnd(booking);
        var end = requestedEnd != null ? requestedEnd
                : current != null && current.getScheduledEndAt() != null ? current.getScheduledEndAt()
                : reservation != null ? reservation.end() : estimatedEnd;
        validateInterval(booking, end);
        var preferredId = booking.getPreferredVehicleId();
        var preferredCategory = booking.getPreferredVehicleCategory() != null
                ? booking.getPreferredVehicleCategory() : booking.getBookingType();
        AssignmentOption preferred = null;
        PreferenceChecks preferredChecks = null;
        String reason = preferredId == null ? "No specific vehicle preference was saved."
                : "The preferred vehicle is no longer in the fleet.";
        var result = new java.util.ArrayList<AssignmentOption>();
        for (Vehicle vehicle : vehicleRepository.findAll()) {
            Driver driver = vehicle.getDriverId() == null ? null : driverRepository.findById(vehicle.getDriverId()).orElse(null);
            boolean isPreferred = vehicle.getVehicleId().equals(preferredId);
            if (isPreferred) preferred = option(vehicle, driver, booking);
            if (current != null && vehicle.getVehicleId().equals(current.getVehicle().getVehicleId())
                    && driver != null && driver.getDriverId().equals(current.getDriver().getDriverId())) {
                if (isPreferred) reason = "This is the current assignment.";
                continue;
            }
            PreferenceChecks checks = assess(booking, driver, vehicle, end, replacementOf);
            String unavailable = checks.reasons().isEmpty() ? null : checks.reasons().get(0);
            if (isPreferred) preferredChecks = checks;
            if (isPreferred) reason = unavailable;
            if (unavailable == null && (current == null || replacementCategoryAllowed(booking, vehicle))
                    && (reservation == null || sameCategory(vehicle.getVehicleType(), preferredCategory))) {
                var option = option(vehicle, driver, booking);
                if (current != null && option.customerAcceptanceRequired()) {
                    option = new AssignmentOption(option.vehicleId(), option.vehicleType(), option.vehicleModel(),
                            option.vehiclePlate(), option.passengerCapacity(), option.luggagePointsCapacity(),
                            option.driverId(), option.driverName(), false);
                }
                result.add(option);
            }
        }
        if (current != null && result.stream().anyMatch(option -> sameCategory(option.vehicleType(), booking.getBookingType()))) {
            result.removeIf(option -> !sameCategory(option.vehicleType(), booking.getBookingType()));
        }
        result.sort(java.util.Comparator
                .comparing((AssignmentOption item) -> current == null || !item.vehicleId().equals(current.getVehicle().getVehicleId()))
                .thenComparing(AssignmentOption::customerAcceptanceRequired)
                .thenComparing(AssignmentOption::passengerCapacity)
                .thenComparing(AssignmentOption::luggagePointsCapacity, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparing(AssignmentOption::vehicleId));
        LocalDateTime pickup = booking.getBookingTripdate().atTime(booking.getBookingPickuptime());
        Long drive = booking.getDurationSeconds();
        Long buffer = drive == null || estimatedEnd == null ? null
                : Duration.between(pickup.plusSeconds(drive), estimatedEnd).getSeconds();
        return new AssignmentPreview(bookingId, bookingReference(bookingId), pickup, preferredId,
                drive, buffer, estimatedEnd, preferredChecks, preferred, preferredCategory, reason, end, result,
                reservation == null ? "NONE" : reservation.expired() ? "EXPIRED" : "ACTIVE",
                reservation == null ? null : reservation.expiresAt(),
                current != null && !pickup.isAfter(malaysiaNow().plusMinutes(reassignmentCutoffMinutes)));
    }

    private AssignmentOption option(Vehicle vehicle, Driver driver, Booking booking) {
        return new AssignmentOption(vehicle.getVehicleId(), vehicle.getVehicleType(), vehicle.getVehicleModel(),
                vehicle.getVehiclePlateno(), vehicle.passengerLimit(), vehicle.getBootCapacityUnits(),
                driver == null ? null : driver.getDriverId(), driver == null ? null : driver.getDriverName(),
                booking.getFareSnapshot() != null && !booking.getFareSnapshot().isBlank() && !sameCategory(vehicle.getVehicleType(), booking.getBookingType()));
    }

    private boolean sameCategory(String first, String second) {
        return first != null && second != null && first.trim().equalsIgnoreCase(second.trim());
    }

    String availabilityReason(Booking booking, Driver driver, Vehicle vehicle,
            java.time.LocalDateTime end, Long excludedAssignmentId) {
        var reasons = assess(booking, driver, vehicle, end, excludedAssignmentId).reasons();
        return reasons.isEmpty() ? null : reasons.get(0);
    }

    private PreferenceChecks assess(Booking booking, Driver driver, Vehicle vehicle,
            LocalDateTime end, Long excludedAssignmentId) {
        var reasons = new ArrayList<String>();
        boolean linked = driver != null && driver.getDriverId().equals(vehicle.getDriverId())
                && vehicleRepository.findByDriverId(driver.getDriverId()).size() == 1;
        if (!linked) reasons.add(driver == null ? "The vehicle has no linked driver."
                : "The driver and vehicle are no longer a valid registered pair.");
        boolean driverAvailable = driver != null && recordCheck(reasons, () -> validateAvailableDriver(driver, end));
        boolean vehicleAvailable = recordCheck(reasons, () -> validateVehicleCondition(vehicle, end));
        boolean capacityCompatible = recordCheck(reasons, () -> validateCapacity(vehicle, booking));
        Boolean scheduleConflict = linked
                ? !recordCheck(reasons, () -> validateSchedule(booking, end, driver, vehicle, excludedAssignmentId))
                : null;
        return new PreferenceChecks(vehicleAvailable, driverAvailable, linked, capacityCompatible,
                scheduleConflict, reasons.isEmpty(), List.copyOf(reasons));
    }

    private boolean recordCheck(List<String> reasons, Runnable validation) {
        try {
            validation.run();
            return true;
        } catch (ResponseStatusException exception) {
            reasons.add(exception.getReason());
            return false;
        }
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
        validateInterval(booking, endAt);
        var startAt = booking.getBookingTripdate().atTime(booking.getBookingPickuptime());
        if (customerTrips != null) {
            String reason = customerTrips.conflictReason(vehicle.getVehicleId(), driver.getDriverId(),
                    startAt, endAt, booking.getBookingId());
            if (reason != null) throw conflict(reason);
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

    private LocalDateTime minimumEnd(Booking booking) {
        var saved = booking.getReservedEndAt();
        if (booking.getBookingTripdate() == null || booking.getBookingPickuptime() == null
                || booking.getDurationSeconds() == null || booking.getDurationSeconds() <= 0) return saved;
        var driveEnd = booking.getBookingTripdate().atTime(booking.getBookingPickuptime())
                .plusSeconds(booking.getDurationSeconds());
        // Saved quotes retain the buffer used when the trip was requested.
        if (saved != null && !saved.isBefore(driveEnd)) return saved;
        return driveEnd.plusMinutes(bufferMinutes);
    }
    private void validateInterval(Booking booking, java.time.LocalDateTime endAt) {
        if (booking.getBookingTripdate() == null || booking.getBookingPickuptime() == null) {
            throw badRequest("A booking pickup date and time are required for scheduling.");
        }
        java.time.LocalDateTime startAt = booking.getBookingTripdate().atTime(booking.getBookingPickuptime());
        if (endAt == null || !endAt.isAfter(startAt)) {
            throw badRequest("Enter a scheduled end time after the booking pickup time.");
        }
        var minimum = minimumEnd(booking);
        if (minimum != null && endAt.isBefore(minimum)) {
            throw badRequest("Scheduled end must include the road travel duration and repositioning buffer.");
        }
    }

    private void validateAvailableDriver(Driver driver, LocalDateTime endAt) {
        if (!"ACTIVE".equalsIgnoreCase(driver.getDriverStatus())) {
            throw conflict("The selected driver account is not active.");
        }
        if (!CustomerTripService.driverCanBeScheduled(driver.getDriverStatus(), driver.getDriverAvailability())) {
            throw conflict("The selected driver is not available.");
        }
        if (endAt != null && driver.getDriverLicenseExpiry() != null
                && !driver.getDriverLicenseExpiry().isBlank()) {
            java.time.LocalDate expiry;
            try {
                expiry = java.time.LocalDate.parse(driver.getDriverLicenseExpiry());
            } catch (java.time.format.DateTimeParseException exception) {
                throw conflict("The selected driver's licence expiry date is invalid. Update the driver record.");
            }
            if (expiry.isBefore(endAt.toLocalDate())) {
                throw conflict("The selected driver's licence expires before the trip ends.");
            }
        }
    }

    private void validateAvailableVehicle(Vehicle vehicle, Booking booking, LocalDateTime endAt) {
        validateVehicleCondition(vehicle, endAt);
        validateCapacity(vehicle, booking);
    }

    private void validateVehicleCondition(Vehicle vehicle, LocalDateTime endAt) {
        if (!Set.of("AVAILABLE", "ACTIVE").contains(
                String.valueOf(vehicle.getVehicleStatus()).toUpperCase(Locale.ROOT))) {
            throw conflict("The selected vehicle is not available.");
        }
        if (endAt != null
                && vehicle.getVehicleRoadtaxexpiry() != null
                && vehicle.getVehicleRoadtaxexpiry().isBefore(endAt.toLocalDate())) {
            throw conflict("The selected vehicle's road tax expires before the trip ends.");
        }
    }

    private void validateCapacity(Vehicle vehicle, Booking booking) {
        // bookingType is a recommendation, not a restriction on Admin alternatives.
        if (booking.getBookingPassengercount() != null
                && vehicle.passengerLimit() < booking.getBookingPassengercount()) {
            throw conflict("The selected vehicle does not have enough passenger capacity.");
        }
        int bootUsage = booking.getLuggageUnits() != null ? booking.getLuggageUnits()
                : Booking.legacyBootUsage(booking.getBookingLuggagecount() == null
                        ? 0 : booking.getBookingLuggagecount(), booking.getBookingLuggagesize());
        if (bootUsage > 0
                && (vehicle.getBootCapacityUnits() == null || vehicle.getBootCapacityUnits() < bootUsage)) {
            throw conflict("The selected vehicle does not have enough boot capacity.");
        }
        if (!vehicle.fitsSeparateLuggage(booking.getLargeCount() == null ? 0 : booking.getLargeCount(),
                booking.getSmallCount() == null ? 0 : booking.getSmallCount())) {
            throw conflict("The selected vehicle does not have enough large luggage or backpack capacity.");
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
                summary.getSmallCount(),summary.getMediumCount(),summary.getLargeCount(),summary.getLuggageUnits(),
                summary.getPreviousAssignmentId(), summary.getReassignmentReason(), summary.getBookingStatus(), summary.getBookingFare(),
                summary.getVehicleModel(), summary.getVehicleCategory(), summary.getCustomerPhone());
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
