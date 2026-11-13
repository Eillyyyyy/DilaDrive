package com.diladrive.service;

import com.diladrive.entity.Booking;
import com.diladrive.dto.CustomerBookingResponse;
import com.diladrive.dto.CustomerBookingRequest;
import com.diladrive.dto.CustomerBookingCreatedResponse;
import com.diladrive.dto.BookingAvailability;
import com.diladrive.dto.VehicleRecommendationResponse;
import com.diladrive.entity.Customer;
import com.diladrive.entity.Vehicle;
import com.diladrive.repository.BookingRepository;
import com.diladrive.repository.AssignmentRepository;
import com.diladrive.repository.BookingRequestIdempotencyRepository;
import com.diladrive.entity.BookingRequestIdempotency;
import com.diladrive.repository.CustomerRepository;
import com.diladrive.repository.DriverRepository;
import com.diladrive.repository.VehicleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Stream;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final NotificationService notificationService;
    private final AssignmentRepository assignmentRepository;
    private final BookingRequestIdempotencyRepository idempotencyRepository;

    @Autowired
    public BookingService(
            BookingRepository bookingRepository,
            CustomerRepository customerRepository,
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            NotificationService notificationService,
            AssignmentRepository assignmentRepository,
            BookingRequestIdempotencyRepository idempotencyRepository) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.notificationService = notificationService;
        this.assignmentRepository = assignmentRepository;
        this.idempotencyRepository = idempotencyRepository;
    }

    public BookingService(
            BookingRepository bookingRepository,
            CustomerRepository customerRepository,
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            NotificationService notificationService,
            AssignmentRepository assignmentRepository) {
        this(bookingRepository, customerRepository, driverRepository, vehicleRepository,
                notificationService, assignmentRepository, null);
    }

    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CustomerBookingResponse> getCustomerBookings(Long customerId) {
        return bookingRepository.findCustomerBookingsNewestFirst(customerId)
                .stream()
                .map(CustomerBookingResponse::from)
                .toList();
    }

    public BookingAvailability checkAvailability(
            String vehicleType,
            LocalDate tripDate,
            LocalTime pickupTime) {
        if (isBlank(vehicleType)
                || tripDate == null
                || pickupTime == null
                || (!vehicleType.equalsIgnoreCase("Sedan")
                && !vehicleType.equalsIgnoreCase("MPV"))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid vehicle type, pickup date, and pickup time are required."
            );
        }

        Set<Long> assignedDrivers = new HashSet<>();
        Set<Long> assignedVehicles = new HashSet<>();
        for (Booking existing : bookingRepository
                .findByBookingTripdateAndBookingPickuptimeAndBookingStatusIgnoreCase(
                        tripDate,
                        pickupTime,
                        "CONFIRMED"
                )) {
            if (existing.getDriverId() != null) {
                assignedDrivers.add(existing.getDriverId());
            }
            if (existing.getVehicleId() != null) {
                assignedVehicles.add(existing.getVehicleId());
            }
        }

        int drivers = (int) driverRepository.findAll().stream()
                .filter(driver -> isActive(driver.getDriverStatus()))
                .filter(driver -> isAvailable(driver.getDriverAvailability()))
                .filter(driver -> !assignedDrivers.contains(driver.getDriverId()))
                .count();

        int vehicles = (int) vehicleRepository.findAll().stream()
                .filter(vehicle -> isAvailable(vehicle.getVehicleStatus()))
                .filter(vehicle -> vehicle.getVehicleType() != null
                        && vehicle.getVehicleType().equalsIgnoreCase(vehicleType))
                .filter(vehicle -> !assignedVehicles.contains(vehicle.getVehicleId()))
                .count();

        return new BookingAvailability(
                drivers,
                vehicles,
                drivers > 0 && vehicles > 0
        );
    }

    @Transactional(readOnly = true)
    public VehicleRecommendationResponse recommendVehicle(
            String pickupAddress,
            String destinationAddress,
            LocalDateTime scheduledAt,
            int passengerCount,
            int luggageCount) {
        if (isBlank(pickupAddress)
                || isBlank(destinationAddress)
                || scheduledAt == null
                || passengerCount < 1
                || luggageCount < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trip locations, schedule, and valid passenger/luggage counts are required.");
        }
        if (scheduledAt.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Pickup date and time cannot be in the past.");
        }

        var suitable = suitableVehicles(scheduledAt, passengerCount, luggageCount)
                .sorted((left, right) -> {
                    int byPassengers = Integer.compare(
                            left.getVehicleCapacity(), right.getVehicleCapacity());
                    if (byPassengers != 0) {
                        return byPassengers;
                    }
                    return Integer.compare(
                            left.getVehicleLuggageCapacity() == null
                                    ? Integer.MAX_VALUE : left.getVehicleLuggageCapacity(),
                            right.getVehicleLuggageCapacity() == null
                                    ? Integer.MAX_VALUE : right.getVehicleLuggageCapacity());
                })
                .findFirst();

        if (suitable.isEmpty()) {
            return new VehicleRecommendationResponse(
                    false,
                    "No currently available driver and vehicle have recorded capacity for this trip.",
                    null);
        }
        var vehicle = suitable.get();
        return new VehicleRecommendationResponse(
                true,
                null,
                new VehicleRecommendationResponse.RecommendedVehicle(
                        vehicle.getVehicleId(),
                        vehicle.getVehicleModel(),
                        vehicle.getVehicleType(),
                        vehicle.getVehicleCapacity(),
                        vehicle.getVehicleLuggageCapacity()));
    }

    private Stream<Vehicle> suitableVehicles(
            LocalDateTime scheduledAt,
            int passengerCount,
            int luggageCount) {
        return vehicleRepository.findByVehicleStatusIgnoreCase("AVAILABLE").stream()
                .filter(vehicle -> vehicle.getVehicleId() != null)
                .filter(vehicle -> !isBlank(vehicle.getVehicleType()))
                .filter(vehicle -> vehicle.getVehicleCapacity() != null
                        && vehicle.getVehicleCapacity() >= passengerCount)
                .filter(vehicle -> luggageCount == 0
                        || (vehicle.getVehicleLuggageCapacity() != null
                        && vehicle.getVehicleLuggageCapacity() >= luggageCount))
                .filter(vehicle -> vehicle.getVehicleRoadtaxexpiry() == null
                        || !vehicle.getVehicleRoadtaxexpiry().isBefore(scheduledAt.toLocalDate()))
                .filter(vehicle -> vehicle.getVehicleServiceexpiry() == null
                        || !vehicle.getVehicleServiceexpiry().isBefore(scheduledAt.toLocalDate()))
                .filter(vehicle -> vehicle.getDriverId() != null)
                .filter(vehicle -> driverRepository.findById(vehicle.getDriverId())
                        .map(driver -> isActive(driver.getDriverStatus())
                                && isAvailable(driver.getDriverAvailability()))
                        .orElse(false));
    }

    public java.util.Map<String, Object> unavailableFareEstimate() {
        return java.util.Map.of(
                "available", false,
                "message", "No fare rules or route-distance estimate are configured. "
                        + "Admin will set the final fare before approving the booking.");
    }

    @Transactional
    public Booking createBooking(Booking request) {
        if (request.getCustId() == null
                || request.getBookingTripdate() == null
                || request.getBookingPickuptime() == null
                || isBlank(request.getBookingPickupaddress())
                || isBlank(request.getBookingDestinationaddress())
                || request.getBookingTripfare() == null
                || isBlank(request.getBookingType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer, trip date/time, both addresses, vehicle type, and fare are required."
            );
        }

        String type = request.getBookingType().trim();
        if (!type.equalsIgnoreCase("Sedan")
                && !type.equalsIgnoreCase("MPV")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vehicle type must be Sedan or MPV."
            );
        }

        if (request.getBookingTripdate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Pickup date cannot be in the past."
            );
        }
        if (request.getBookingTripdate().isEqual(LocalDate.now())
                && request.getBookingPickuptime().isBefore(LocalTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Pickup time cannot be in the past."
            );
        }

        request.setBookingType(type);
        request.setDriverId(null);
        request.setVehicleId(null);
        request.setBookingStatus("PENDING_VERIFICATION");
        request.setBookingCreatedat(LocalDateTime.now());
        return bookingRepository.save(request);
    }

    @Transactional
    public CustomerBookingCreatedResponse createCustomerBooking(
            Long customerId,
            CustomerBookingRequest request) {
        return createCustomerBooking(customerId, request, null);
    }

    @Transactional
    public CustomerBookingCreatedResponse createCustomerBooking(
            Long customerId,
            CustomerBookingRequest request,
            String idempotencyKey) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer account not found."));
        if (!"ACTIVE".equalsIgnoreCase(customer.getCustStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "An Admin must verify your customer account before you can book.");
        }

        BookingRequestIdempotency idempotency = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String key = idempotencyKey.trim();
            if (key.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The booking request key is too long.");
            }
            if (idempotencyRepository == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Booking retry protection is not configured.");
            }
            String requestHash = bookingRequestHash(request);
            idempotencyRepository.insertIfAbsent(customerId, key, requestHash);
            idempotency = idempotencyRepository.findForUpdate(customerId, key)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Booking retry protection could not be initialized."));
            if (!requestHash.equals(idempotency.getRequestHash())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This booking request key was already used for different trip details.");
            }
            if (idempotency.getBookingId() != null) {
                String responseStatus = idempotency.getResponseStatus();
                return new CustomerBookingCreatedResponse(
                        "BK-%06d".formatted(idempotency.getBookingId()),
                        responseStatus == null ? "PENDING_VERIFICATION" : responseStatus);
            }
        }

        String vehicleType = request.bookingType().trim();
        boolean knownVehicleType = vehicleRepository.findAll().stream()
                .anyMatch(vehicle -> vehicle.getVehicleType() != null
                        && vehicle.getVehicleType().equalsIgnoreCase(vehicleType));
        if (!knownVehicleType) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose a vehicle type recorded in the DilaDrive fleet.");
        }
        String tripType = request.tripType().trim().toUpperCase();
        if (!Set.of("AIRPORT_PICKUP", "AIRPORT_DROPOFF", "NORMAL_TRANSFER")
                .contains(tripType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose a supported trip type.");
        }

        LocalTime scheduledTime = "AIRPORT_PICKUP".equals(tripType)
                ? request.landedTime()
                : request.pickupTime();
        if (scheduledTime == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A pickup time is required for this trip.");
        }
        if ("AIRPORT_PICKUP".equals(tripType)
                && isBlank(request.flightNumber())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A flight number is required for airport pickup.");
        }
        if (request.pickupAddress().trim().equalsIgnoreCase(
                request.destinationAddress().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Pickup and destination must be different locations.");
        }
        if (request.luggageCount() > 0 && isBlank(request.luggageSize())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Select a luggage size when luggage is included.");
        }
        if (!isBlank(request.luggageSize())
                && !Set.of("SMALL", "MEDIUM", "LARGE")
                .contains(request.luggageSize().trim().toUpperCase())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose a valid luggage size.");
        }
        if (request.bookingDate().isBefore(LocalDate.now())
                || (request.bookingDate().isEqual(LocalDate.now())
                && scheduledTime.isBefore(LocalTime.now()))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Pickup date and time cannot be in the past.");
        }

        LocalDateTime scheduledAt = LocalDateTime.of(request.bookingDate(), scheduledTime);
        boolean selectedTypeAvailable = suitableVehicles(
                scheduledAt, request.passengerCount(), request.luggageCount())
                .anyMatch(vehicle -> vehicle.getVehicleType().equalsIgnoreCase(vehicleType));
        if (!selectedTypeAvailable) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The selected vehicle type is no longer available for this trip. Check availability again.");
        }

        Booking booking = new Booking();
        booking.setCustId(customerId);
        booking.setBookingType(vehicleType);
        booking.setBookingTriptype(tripType);
        booking.setBookingTripdate(request.bookingDate());
        booking.setBookingPickuptime(scheduledTime);
        booking.setBookingPickupaddress(request.pickupAddress().trim());
        booking.setBookingDestinationaddress(request.destinationAddress().trim());
        booking.setBookingFlightno("NORMAL_TRANSFER".equals(tripType)
                ? null : normalizeOptional(request.flightNumber()));
        booking.setBookingPassengercount(request.passengerCount());
        booking.setBookingLuggagecount(request.luggageCount());
        booking.setBookingLuggagesize(
                request.luggageCount() == 0 ? null : normalizeOptional(request.luggageSize()));
        booking.setBookingNotes(normalizeOptional(request.additionalNotes()));
        // Fare remains unset until pricing data exists and Admin finalizes the amount.
        booking.setBookingTripfare(null);
        booking.setDriverId(null);
        booking.setVehicleId(null);
        booking.setBookingStatus("PENDING_VERIFICATION");
        booking.setBookingCreatedat(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);
        notificationService.notifyAdminsOfNewBooking(saved.getBookingId());
        if (idempotency != null) {
            idempotency.setBookingId(saved.getBookingId());
            idempotency.setResponseStatus(saved.getBookingStatus());
            idempotencyRepository.save(idempotency);
        }
        notificationService.createCustomerNotification(
                customerId,
                "Booking request received",
                "Your booking " + bookingReference(saved.getBookingId())
                        + " was submitted and is waiting for Admin verification.",
                "BOOKING");
        return CustomerBookingCreatedResponse.from(saved);
    }

    private String bookingRequestHash(CustomerBookingRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(request.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    @Transactional
    public Booking verifyBooking(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Booking not found."
                ));

        if ("CANCELLED".equalsIgnoreCase(booking.getBookingStatus())
                || "COMPLETED".equalsIgnoreCase(booking.getBookingStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cancelled or completed bookings cannot be verified."
            );
        }
        if ("CONFIRMED".equalsIgnoreCase(booking.getBookingStatus())) {
            return booking;
        }
        if (booking.getBookingTripfare() == null
                || booking.getBookingTripfare().signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Set the final fare before confirming this booking.");
        }

        booking.setBookingStatus("CONFIRMED");
        Booking saved = bookingRepository.save(booking);
        if (booking.getCustId() != null) {
            notificationService.createCustomerNotification(
                    booking.getCustId(),
                    "Booking approved",
                    "Your booking " + bookingReference(booking.getBookingId())
                            + " has been approved by Admin.",
                    "BOOKING_APPROVED");
        }
        return saved;
    }

    @Transactional
    public Booking updateBookingFare(Long bookingId, java.math.BigDecimal tripFare) {
        if (tripFare == null || tripFare.signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Final fare must be greater than zero.");
        }
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Booking not found."));
        if (!"PENDING_VERIFICATION".equalsIgnoreCase(booking.getBookingStatus())
                && !"PENDING".equalsIgnoreCase(booking.getBookingStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The fare can only be set before a booking is confirmed.");
        }
        booking.setBookingTripfare(tripFare);
        return bookingRepository.save(booking);
    }

    @Transactional
    public CustomerBookingResponse cancelCustomerBooking(Long customerId, Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found."));
        if (!customerId.equals(booking.getCustId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Booking not found.");
        }

        String status = booking.getBookingStatus() == null
                ? ""
                : booking.getBookingStatus().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "PENDING_VERIFICATION", "CONFIRMED").contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This booking can no longer be cancelled by the customer.");
        }
        if (assignmentRepository.existsByBookingBookingIdAndAssignmentStatusNotIn(
                bookingId, List.of("COMPLETED", "CANCELLED"))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A booking with an active driver assignment cannot be cancelled here.");
        }

        booking.setBookingStatus("CANCELLED");
        Booking saved = bookingRepository.save(booking);
        notificationService.createCustomerNotification(
                customerId,
                "Booking cancelled",
                "Your booking " + bookingReference(bookingId) + " has been cancelled.",
                "BOOKING_CANCELLED");
        return CustomerBookingResponse.from(saved);
    }

    @Transactional
    public CustomerBookingResponse updateCustomerBooking(
            Long customerId, Long bookingId, CustomerBookingRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customer account not found."));
        if (!"ACTIVE".equalsIgnoreCase(customer.getCustStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "An Admin must verify your customer account before you can edit a booking.");
        }

        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found."));
        if (!customerId.equals(booking.getCustId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found.");
        }
        String status = booking.getBookingStatus() == null
                ? "" : booking.getBookingStatus().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "PENDING_VERIFICATION").contains(status)
                || assignmentRepository.existsByBookingBookingIdAndAssignmentStatusNotIn(
                        bookingId, List.of("COMPLETED", "CANCELLED"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a pending booking without an active driver assignment can be edited.");
        }

        String vehicleType = request.bookingType().trim();
        boolean knownVehicleType = vehicleRepository.findAll().stream()
                .anyMatch(vehicle -> vehicle.getVehicleType() != null
                        && vehicle.getVehicleType().equalsIgnoreCase(vehicleType));
        if (!knownVehicleType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose a vehicle type recorded in the DilaDrive fleet.");
        }
        String tripType = request.tripType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("AIRPORT_PICKUP", "AIRPORT_DROPOFF", "NORMAL_TRANSFER")
                .contains(tripType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose a supported trip type.");
        }
        LocalTime scheduledTime = "AIRPORT_PICKUP".equals(tripType)
                ? request.landedTime() : request.pickupTime();
        if (scheduledTime == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A pickup time is required for this trip.");
        }
        if ("AIRPORT_PICKUP".equals(tripType) && isBlank(request.flightNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A flight number is required for airport pickup.");
        }
        if (request.pickupAddress().trim().equalsIgnoreCase(
                request.destinationAddress().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Pickup and destination must be different locations.");
        }
        if (request.luggageCount() > 0 && isBlank(request.luggageSize())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Select a luggage size when luggage is included.");
        }
        if (!isBlank(request.luggageSize())
                && !Set.of("SMALL", "MEDIUM", "LARGE")
                        .contains(request.luggageSize().trim().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose a valid luggage size.");
        }
        if (request.bookingDate().isBefore(LocalDate.now())
                || (request.bookingDate().isEqual(LocalDate.now())
                && scheduledTime.isBefore(LocalTime.now()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Pickup date and time cannot be in the past.");
        }

        LocalDateTime scheduledAt = LocalDateTime.of(request.bookingDate(), scheduledTime);
        boolean selectedTypeAvailable = suitableVehicles(
                scheduledAt, request.passengerCount(), request.luggageCount())
                .anyMatch(vehicle -> vehicle.getVehicleType().equalsIgnoreCase(vehicleType));
        if (!selectedTypeAvailable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The selected vehicle type is no longer available for this trip. Check availability again.");
        }

        booking.setBookingType(vehicleType);
        booking.setBookingTriptype(tripType);
        booking.setBookingTripdate(request.bookingDate());
        booking.setBookingPickuptime(scheduledTime);
        booking.setBookingPickupaddress(request.pickupAddress().trim());
        booking.setBookingDestinationaddress(request.destinationAddress().trim());
        booking.setBookingFlightno("NORMAL_TRANSFER".equals(tripType)
                ? null : normalizeOptional(request.flightNumber()));
        booking.setBookingPassengercount(request.passengerCount());
        booking.setBookingLuggagecount(request.luggageCount());
        booking.setBookingLuggagesize(request.luggageCount() == 0
                ? null : normalizeOptional(request.luggageSize()));
        booking.setBookingNotes(normalizeOptional(request.additionalNotes()));
        // Any fare attached to an unverified request is stale after its trip changes.
        booking.setBookingTripfare(null);
        Booking saved = bookingRepository.save(booking);
        notificationService.createCustomerNotification(
                customerId,
                "Booking request updated",
                "Your booking " + bookingReference(bookingId)
                        + " was updated and remains pending Admin verification.",
                "BOOKING_UPDATED");
        return CustomerBookingResponse.from(saved);
    }

    private boolean isAvailable(String status) {
        if (status == null) {
            return false;
        }
        return "AVAILABLE".equalsIgnoreCase(status.trim());
    }

    private boolean isActive(String status) {
        return status != null && "ACTIVE".equalsIgnoreCase(status.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeOptional(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String bookingReference(Long bookingId) {
        return "BK-" + String.format("%06d", bookingId);
    }
}
