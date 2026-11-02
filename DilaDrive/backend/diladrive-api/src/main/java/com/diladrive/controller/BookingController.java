package com.diladrive.controller;

import com.diladrive.entity.Booking;
import com.diladrive.repository.BookingRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingRepository repository;

    public BookingController(BookingRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Booking> getAllBookings() {
        return repository.findAll();
    }
}