package com.diladrive.controller;

import com.diladrive.entity.Booking;
import com.diladrive.dto.BookingAvailability;
import com.diladrive.dto.CustomerBookingResponse;
import com.diladrive.dto.CustomerBookingRequest;
import com.diladrive.dto.CustomerBookingCreatedResponse;
import com.diladrive.dto.BookingFareRequest;
import com.diladrive.dto.VehicleRecommendationResponse;
import com.diladrive.service.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(
            BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public List<Booking> getAllBookings(HttpServletRequest request) {
        requireAdminId(request);
        return bookingService.getAllBookings();
    }

    @GetMapping("/mine")
    public List<CustomerBookingResponse> getMyBookings(HttpServletRequest request) {
        return bookingService.getCustomerBookings(requireCustomerId(request));
    }

    @GetMapping("/availability/recommendation")
    public VehicleRecommendationResponse recommendVehicle(
            @RequestParam String pickupAddress,
            @RequestParam String destinationAddress,
            @RequestParam LocalDateTime scheduledAt,
            @RequestParam String tripType,
            @RequestParam int passengerCount,
            @RequestParam int luggageCount,
            @RequestParam(required = false) String luggageSize,
            HttpServletRequest request) {
        requireCustomerId(request);
        if (luggageCount > 0 && (luggageSize == null || luggageSize.isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Select a luggage size when luggage is included.");
        }
        if (tripType == null || !List.of(
                "airportPickup", "airportDropoff", "normalTransfer",
                "AIRPORT_PICKUP", "AIRPORT_DROPOFF", "NORMAL_TRANSFER")
                .contains(tripType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose a supported trip type.");
        }
        return bookingService.recommendVehicle(
                pickupAddress,
                destinationAddress,
                scheduledAt,
                passengerCount,
                luggageCount);
    }

    @GetMapping("/fare-estimate")
    public Map<String, Object> getFareEstimate(HttpServletRequest request) {
        requireCustomerId(request);
        return bookingService.unavailableFareEstimate();
    }

    @PostMapping("/customer")
    public ResponseEntity<CustomerBookingCreatedResponse> createCustomerBooking(
            @Valid @RequestBody CustomerBookingRequest booking,
            HttpServletRequest request) {
        CustomerBookingCreatedResponse created = bookingService.createCustomerBooking(
                requireCustomerId(request), booking,
                request.getHeader("Idempotency-Key"));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/availability")
    public BookingAvailability checkAvailability(
            @RequestParam String vehicleType,
            @RequestParam LocalDate tripDate,
            @RequestParam LocalTime pickupTime,
            HttpServletRequest request) {
        // Availability counts are operational data; expose them only to admins
        // and signed-in customers using the existing session model.
        requireAdminOrCustomer(request);
        return bookingService.checkAvailability(
                vehicleType,
                tripDate,
                pickupTime
        );
    }

    @PostMapping
    public ResponseEntity<Booking> createBooking(
            @RequestBody Booking booking,
            HttpServletRequest request) {
        requireAdminId(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.createBooking(booking));
    }

    @PutMapping("/{id}/verify")
    public Booking verifyBooking(
            @PathVariable Long id,
            HttpServletRequest request) {
        requireAdminId(request);
        return bookingService.verifyBooking(id);
    }

    @PutMapping("/{id}/fare")
    public Booking updateBookingFare(
            @PathVariable Long id,
            @Valid @RequestBody BookingFareRequest request,
            HttpServletRequest servletRequest) {
        requireAdminId(servletRequest);
        return bookingService.updateBookingFare(id, request.tripFare());
    }

    @PostMapping("/{id}/cancel")
    public CustomerBookingResponse cancelMyBooking(
            @PathVariable Long id,
            HttpServletRequest request) {
        return bookingService.cancelCustomerBooking(requireCustomerId(request), id);
    }

    @PutMapping("/{id}/customer")
    public CustomerBookingResponse updateMyPendingBooking(
            @PathVariable Long id,
            @Valid @RequestBody CustomerBookingRequest booking,
            HttpServletRequest request) {
        return bookingService.updateCustomerBooking(
                requireCustomerId(request), id, booking);
    }

    private static Long requireAdminId(HttpServletRequest request) {
        Object adminId = sessionAttribute(request, "adminId");
        if (adminId instanceof Long id) {
            return id;
        }
        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Please sign in as an admin.");
    }

    private static Long requireCustomerId(HttpServletRequest request) {
        Object role = sessionAttribute(request, "mobileRole");
        Object userId = sessionAttribute(request, "mobileUserId");
        if (!(userId instanceof Long id)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Please sign in again.");
        }
        if (!"CUSTOMER".equals(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This booking list is only available to customers.");
        }
        return id;
    }

    private static void requireAdminOrCustomer(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Please sign in to check availability.");
        }
        if (session.getAttribute("adminId") instanceof Long) return;
        Object userId = session.getAttribute("mobileUserId");
        if (userId instanceof Long && "CUSTOMER".equals(session.getAttribute("mobileRole"))) {
            return;
        }
        if (userId instanceof Long || session.getAttribute("mobileRole") != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Availability is only available to admins and customers.");
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Please sign in to check availability.");
    }

    private static Object sessionAttribute(HttpServletRequest request, String key) {
        var session = request.getSession(false);
        return session == null ? null : session.getAttribute(key);
    }
}
