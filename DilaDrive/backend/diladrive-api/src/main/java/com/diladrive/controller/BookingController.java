package com.diladrive.controller;

import com.diladrive.entity.Booking;
import com.diladrive.dto.BookingAvailability;
import com.diladrive.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/bookings")
@CrossOrigin(origins = "http://localhost:5173")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(
            BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public List<Booking> getAllBookings() {
        return bookingService.getAllBookings();
    }

    @GetMapping("/availability")
    public BookingAvailability checkAvailability(
            @RequestParam String vehicleType,
            @RequestParam LocalDate tripDate,
            @RequestParam LocalTime pickupTime) {
        return bookingService.checkAvailability(
                vehicleType,
                tripDate,
                pickupTime
        );
    }

    @PostMapping
    public ResponseEntity<Booking> createBooking(
            @RequestBody Booking booking) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.createBooking(booking));
    }

    @PutMapping("/{id}/verify")
    public Booking verifyBooking(@PathVariable Long id) {
        return bookingService.verifyBooking(id);
    }
}