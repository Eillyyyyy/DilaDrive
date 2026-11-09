package com.diladrive.service;

import com.diladrive.entity.Booking;
import com.diladrive.dto.BookingAvailability;
import com.diladrive.repository.BookingRepository;
import com.diladrive.repository.DriverRepository;
import com.diladrive.repository.VehicleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;

    public BookingService(
            BookingRepository bookingRepository,
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository) {
        this.bookingRepository = bookingRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
    }

    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
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

        booking.setBookingStatus("CONFIRMED");
        return bookingRepository.save(booking);
    }

    private boolean isAvailable(String status) {
        if (status == null) {
            return false;
        }
        return "AVAILABLE".equalsIgnoreCase(status.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}