package com.diladrive.service;

import com.diladrive.dto.DriverCreateRequest;
import com.diladrive.dto.DriverDetailsResponse;
import com.diladrive.dto.DriverUpdateRequest;
import com.diladrive.dto.DriverProfileResponse;
import com.diladrive.dto.DriverProfileUpdateRequest;
import com.diladrive.dto.VehicleCreateRequest;
import com.diladrive.entity.Driver;
import com.diladrive.entity.Vehicle;
import com.diladrive.repository.DriverRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final VehicleService vehicleService;
    private final com.diladrive.repository.AssignmentRepository assignments;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DriverService(
            DriverRepository driverRepository,
            VehicleService vehicleService, com.diladrive.repository.AssignmentRepository assignments) {
        this.driverRepository = driverRepository;
        this.vehicleService = vehicleService;
        this.assignments = assignments;
    }

    public List<Driver> getAllDrivers() {
        return driverRepository.findAll();
    }

    public List<Driver> getAvailableDrivers() {
        return driverRepository.findByDriverAvailabilityIgnoreCaseAndDriverStatusIgnoreCase(
                "AVAILABLE", "ACTIVE");
    }

    @Transactional(readOnly = true)
    public DriverProfileResponse getProfile(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Driver not found."));
        return profileResponse(driver);
    }

    @Transactional
    public DriverProfileResponse updateProfile(
            Long driverId, DriverProfileUpdateRequest request) {
        Driver driver = driverRepository.findByIdForUpdate(driverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Driver not found."));
        String email = normalizeEmail(request.email());
        try {
            ensureEmailIsAvailable(email, driverId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This email address already exists.");
        }
        if (request.profileImage() != null) driver.setProfileImage(validateProfileImage(request.profileImage()));
        driver.setDriverName(request.name().trim());
        driver.setDriverEmail(email);
        driver.setDriverPhoneno(request.phone().trim());
        return profileResponse(driverRepository.save(driver));
    }

    private DriverProfileResponse profileResponse(Driver driver) {
        var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kuala_Lumpur"));
        Long id = driver.getDriverId();
        return DriverProfileResponse.from(driver, vehicleService.getAssignedVehicle(id),
                assignments.countByDriverDriverId(id),
                assignments.countByDriverDriverIdAndAssignmentStatusIgnoreCase(id, "COMPLETED"),
                assignments.countUpcomingDriverTrips(id, now.toLocalDate(), now.toLocalTime()));
    }

    @Transactional
    public void changeDriverPassword(Long id, com.diladrive.dto.ChangePasswordRequest request) {
        Driver driver = driverRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found."));
        PasswordService passwords = new PasswordService();
        if (request == null || request.getCurrentPassword() == null
                || request.getCurrentPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !passwords.matches(request.getCurrentPassword(), driver.getDriverPassword())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current password is incorrect.");
        }
        String encoded = encodePassword(request.getNewPassword());
        if (passwords.matches(request.getNewPassword(), driver.getDriverPassword())) {
            throw new IllegalArgumentException("Choose a password different from your current password.");
        }
        driver.setDriverPassword(encoded);
        driverRepository.save(driver);
    }

    private String validateProfileImage(String image) {
        if (image.isEmpty()) return null;
        String message = "Choose a valid PNG, JPEG or WebP image smaller than 900 KB.";
        if (image.length() > 1300000 || !image.matches("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1));
            boolean png = bytes.length >= 8 && bytes[0] == (byte)137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71;
            boolean jpeg = bytes.length >= 3 && bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255;
            boolean webp = bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            boolean matches = (image.startsWith("data:image/png;") && png) || (image.startsWith("data:image/jpeg;") && jpeg)
                    || (image.startsWith("data:image/webp;") && webp);
            if (bytes.length > 900 * 1024 || !matches) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return image;
    }

    @Transactional
    public Driver createDriver(DriverCreateRequest request) {
        if (request == null || request.vehicle() == null) {
            throw new IllegalArgumentException("Driver and vehicle details are required.");
        }

        Driver driver = new Driver();
        driver.setDriverName(request.driverName());
        driver.setDriverEmail(request.driverEmail());
        driver.setDriverPassword(request.driverPassword());
        driver.setDriverPhoneno(request.driverPhoneno());
        driver.setDriverIcno(request.driverIcno());
        driver.setDriverLicenseno(request.driverLicenseno());
        driver.setDriverLicenseExpiry(request.driverLicenseExpiry());
        driver.setDriverType(request.driverType());
        driver.setDriverAvailability(request.driverAvailability());
        driver.setDriverStatus(request.driverStatus());

        validateProfile(driver);
        driver.setDriverName(driver.getDriverName().trim());
        driver.setDriverEmail(normalizeEmail(driver.getDriverEmail()));
        driver.setDriverPhoneno(driver.getDriverPhoneno().trim());
        ensureEmailIsAvailable(driver.getDriverEmail(), null);
        driver.setDriverPassword(encodePassword(driver.getDriverPassword()));

        // Only an administrator can create a driver; no approval stage applies.
        driver.setDriverStatus("ACTIVE");

        if (driver.getDriverAvailability() == null
                || driver.getDriverAvailability().isBlank()) {
            driver.setDriverAvailability("AVAILABLE");
        } else {
            driver.setDriverAvailability(normalizeAvailability(
                    driver.getDriverAvailability()));
        }

        Driver savedDriver = driverRepository.save(driver);
        vehicleService.createVehicleForDriver(toVehicle(request.vehicle()), savedDriver.getDriverId());
        return savedDriver;
    }

    @Transactional
    public Vehicle registerMissingVehicle(Long driverId, VehicleCreateRequest request) {
        driverRepository.findByIdForUpdate(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found."));
        return vehicleService.createVehicleForDriver(toVehicle(request), driverId);
    }

    @Transactional(readOnly = true)
    public DriverDetailsResponse getDriver(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found."));
        return new DriverDetailsResponse(driver, vehicleService.getAssignedVehicle(id));
    }

    @Transactional
    public DriverDetailsResponse updateDriverDetails(Long id, DriverUpdateRequest request) {
        driverRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found."));
        if (request.isRegisterMissingVehicle() && request.getVehicle() == null) {
            throw new IllegalArgumentException("Vehicle details are required to complete registration.");
        }
        Vehicle vehicle = request.getVehicle() == null ? null
                : request.isRegisterMissingVehicle()
                    ? vehicleService.createVehicleForDriver(toVehicle(request.getVehicle()), id)
                    : vehicleService.updateAssignedVehicle(id, toVehicle(request.getVehicle()));
        return new DriverDetailsResponse(updateDriver(id, request), vehicle);
    }

    @Transactional
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
        existing.setDriverAvailability(driver.getDriverAvailability() == null
                || driver.getDriverAvailability().isBlank() ? existing.getDriverAvailability()
                : normalizeAvailability(driver.getDriverAvailability()));

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
        if (!List.of("ACTIVE", "DISABLED").contains(normalized)) {
            throw new IllegalArgumentException("Invalid driver account status.");
        }
        return normalized;
    }

    private String normalizeAvailability(String availability) {
        String normalized = availability.trim().toUpperCase(Locale.ROOT);
        if (!List.of("AVAILABLE", "BUSY", "UNAVAILABLE").contains(normalized)) {
            throw new IllegalArgumentException("Invalid driver availability.");
        }
        return normalized;
    }

    private Vehicle toVehicle(VehicleCreateRequest request) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleModel(request.vehicleModel());
        vehicle.setVehiclePlateno(request.vehiclePlateno());
        vehicle.setVehicleType(request.vehicleType());
        vehicle.setVehicleCapacity(request.vehicleCapacity());
        vehicle.setVehicleLuggageCapacity(request.vehicleLuggageCapacity());
        vehicle.setVehicleRoadtaxexpiry(request.vehicleRoadtaxexpiry());
        vehicle.setVehicleServiceexpiry(request.vehicleServiceexpiry());
        vehicle.setVehicleStatus(request.vehicleStatus());
        return vehicle;
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
