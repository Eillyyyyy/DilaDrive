package com.diladrive.service;

import com.diladrive.entity.Driver;
import com.diladrive.repository.DriverRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DriverService(
            DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    public List<Driver> getAllDrivers() {
        return driverRepository.findAll();
    }

    public List<Driver> getAvailableDrivers() {
        return driverRepository.findByDriverAvailabilityIgnoreCase("AVAILABLE");
    }

    public Driver createDriver(Driver driver) {
        validateProfile(driver);
        driver.setDriverName(driver.getDriverName().trim());
        driver.setDriverEmail(normalizeEmail(driver.getDriverEmail()));
        driver.setDriverPhoneno(driver.getDriverPhoneno().trim());
        ensureEmailIsAvailable(driver.getDriverEmail(), null);
        driver.setDriverPassword(encodePassword(driver.getDriverPassword()));

        if (driver.getDriverStatus() == null || driver.getDriverStatus().isBlank()) {
            driver.setDriverStatus("PENDING");
        } else {
            driver.setDriverStatus(normalizeStatus(driver.getDriverStatus()));
        }

        return driverRepository.save(driver);
    }

    public Driver updateDriver(
            Long id,
            Driver driver) {

        Driver existing =
                driverRepository.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Driver not found."));

        validateProfile(driver);
        String email = normalizeEmail(driver.getDriverEmail());
        ensureEmailIsAvailable(email, id);

        existing.setDriverName(driver.getDriverName().trim());
        existing.setDriverEmail(email);
        if (driver.getDriverPassword() != null
                && !driver.getDriverPassword().isBlank()) {
            existing.setDriverPassword(encodePassword(driver.getDriverPassword()));
        }
        existing.setDriverPhoneno(driver.getDriverPhoneno().trim());
        existing.setDriverStatus(driver.getDriverStatus() == null
                || driver.getDriverStatus().isBlank()
                ? existing.getDriverStatus()
                : normalizeStatus(driver.getDriverStatus()));
        existing.setDriverIcno(driver.getDriverIcno());
        existing.setDriverLicenseno(driver.getDriverLicenseno());
        existing.setDriverLicenseExpiry(driver.getDriverLicenseExpiry());
        existing.setDriverType(driver.getDriverType());
        existing.setDriverAvailability(driver.getDriverAvailability());

        return driverRepository.save(existing);
    }

    public void deleteDriver(Long id) {
        if (!driverRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found.");
        }
        driverRepository.deleteById(id);
    }

    private void validateProfile(Driver driver) {
        if (driver.getDriverName() == null || driver.getDriverName().isBlank()
                || driver.getDriverEmail() == null || driver.getDriverEmail().isBlank()
                || driver.getDriverPhoneno() == null || driver.getDriverPhoneno().isBlank()) {
            throw new IllegalArgumentException("Name, email and phone number are required.");
        }

        String email = normalizeEmail(driver.getDriverEmail());
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
    }

    private void ensureEmailIsAvailable(String email, Long currentId) {
        driverRepository.findByDriverEmailIgnoreCase(email)
                .filter(existing -> !existing.getDriverId().equals(currentId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("This email address already exists.");
                });
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PENDING", "ACTIVE", "DISABLED").contains(normalized)) {
            throw new IllegalArgumentException("Invalid driver account status.");
        }
        return normalized;
    }

    private String encodePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("A password is required.");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("The password must contain at least 8 characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("The password must not exceed 72 UTF-8 bytes.");
        }
        return passwordEncoder.encode(password);
    }
}