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
import com.diladrive.dto.FleetDocumentMetadata;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final VehicleService vehicleService;
    private final com.diladrive.repository.AssignmentRepository assignments;
    private final FleetDataProtection dataProtection;
    private final FleetDocumentService fleetDocuments;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DriverService(
            DriverRepository driverRepository,
            VehicleService vehicleService, com.diladrive.repository.AssignmentRepository assignments) {
        this(driverRepository, vehicleService, assignments, null, null);
    }

    @Autowired
    public DriverService(DriverRepository driverRepository, VehicleService vehicleService,
            com.diladrive.repository.AssignmentRepository assignments,
            FleetDataProtection dataProtection, FleetDocumentService fleetDocuments) {
        this.driverRepository = driverRepository;
        this.vehicleService = vehicleService;
        this.assignments = assignments;
        this.dataProtection = dataProtection;
        this.fleetDocuments = fleetDocuments;
    }

    public List<Driver> getAllDrivers() {
        return driverRepository.findAll();
    }

    public List<Driver> getAvailableDrivers() {
        return driverRepository.findByDriverAvailabilityIgnoreCaseAndDriverStatusIgnoreCase(
                "AVAILABLE", "ACTIVE");
    }

    @Transactional(readOnly = true)
    public String maskedIc(Driver driver) {
        String normalized = null;
        if (driver.getDriverIcnoCiphertext() != null && dataProtection != null) {
            normalized = dataProtection.decryptIc(driver.getDriverIcnoCiphertext());
        } else if (driver.getDriverIcno() != null) {
            normalized = normalizeLegacyIc(driver.getDriverIcno());
        }
        if (normalized == null || normalized.length() < 4) return null;
        String digits = normalized.replaceAll("\\D", "");
        return digits.length() < 4 ? null : "******-**-" + digits.substring(digits.length() - 4);
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
        if (request.profileImage() != null) driver.setProfileImage(ProfileUpdateValidation.mobileImage(request.profileImage()));
        driver.setDriverName(request.name().trim());
        driver.setDriverEmail(email);
        driver.setDriverPhoneno(ProfileUpdateValidation.phone(request.phone()));
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
        driver.setDriverLicenseno(request.driverLicenseno());
        driver.setDriverLicenseExpiry(request.driverLicenseExpiry());
        driver.setDriverType(request.driverType());
        driver.setDriverAvailability(request.driverAvailability());
        driver.setDriverStatus(request.driverStatus());
        if (request.driverIcno() != null && !request.driverIcno().isBlank()) {
            setEncryptedIc(driver, request.driverIcno(), null);
        }

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
    public DriverDetailsResponse createDriverWithDocuments(DriverCreateRequest request,
            MultipartFile icDocument, MultipartFile licenseDocument,
            MultipartFile roadTaxDocument, Long adminId) {
        if (request == null) throw new IllegalArgumentException("Driver details are required.");
        if (request.driverLicenseno() == null || request.driverLicenseno().isBlank()
                || request.driverLicenseExpiry() == null || request.driverLicenseExpiry().isBlank()) {
            throw new IllegalArgumentException("Driving licence number and expiry date are required.");
        }
        validateLicenseExpiry(request.driverLicenseExpiry());
        if (request.driverType() == null
                || !List.of("Company Driver", "Associate Driver").contains(request.driverType())) {
            throw new IllegalArgumentException("Choose a valid driver type.");
        }
        if (licenseDocument == null || licenseDocument.isEmpty()
                || roadTaxDocument == null || roadTaxDocument.isEmpty()) {
            throw new IllegalArgumentException("Driving licence and road tax documents are required.");
        }
        if (request.vehicle() == null) {
            throw new IllegalArgumentException("Vehicle details are required.");
        }
        Vehicle newVehicle = toVehicle(request.vehicle());
        vehicleService.validateNewVehicleCategory(newVehicle);
        if (!java.util.Objects.equals(request.vehicle().vehicleCapacity(), newVehicle.getVehicleCapacity())) {
            throw new IllegalArgumentException("Passenger capacity must match the selected category. Refresh and select it again.");
        }
        if (!java.util.Objects.equals(request.vehicle().vehicleLuggageCapacity(), newVehicle.getBootCapacityUnits())) {
            throw new IllegalArgumentException("Luggage capacity must match the selected category. Refresh and select it again.");
        }
        if (newVehicle.getVehicleRoadtaxexpiry() == null
                || newVehicle.getVehicleServiceexpiry() == null) {
            throw new IllegalArgumentException("Road tax expiry and next service dates are required.");
        }
        if (!dataProtection.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Encrypted fleet identity storage is not configured.");
        }

        Driver driver = createDriver(request);
        Vehicle vehicle = vehicleService.getAssignedVehicle(driver.getDriverId());
        if (vehicle == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The driver's vehicle could not be linked.");
        }
        if (icDocument != null && !icDocument.isEmpty()) {
            uploadDocument("IC document", () ->
                    fleetDocuments.uploadDriverDocument(driver.getDriverId(), "DRIVER_IC", icDocument, adminId));
        }
        uploadDocument("Driving licence document", () ->
                fleetDocuments.uploadDriverDocument(driver.getDriverId(), "DRIVER_LICENSE", licenseDocument, adminId));
        uploadDocument("Road tax document", () ->
                fleetDocuments.uploadVehicleRoadTax(vehicle.getVehicleId(), roadTaxDocument, adminId));
        return getDriver(driver.getDriverId());
    }

    private void uploadDocument(String field, Runnable upload) {
        try {
            upload.run();
        } catch (ResponseStatusException error) {
            if (error.getStatusCode().value() == 400) {
                throw new IllegalArgumentException(field + ": " + error.getReason(), error);
            }
            throw error;
        }
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
        Vehicle vehicle = vehicleService.getAssignedVehicle(id);
        List<FleetDocumentMetadata> metadata = fleetDocuments == null
                ? List.of() : fleetDocuments.currentDriverDocuments(id);
        FleetDocumentMetadata ic = metadata.stream()
                .filter(item -> "DRIVER_IC".equals(item.type())).findFirst().orElse(null);
        FleetDocumentMetadata license = metadata.stream()
                .filter(item -> "DRIVER_LICENSE".equals(item.type())).findFirst().orElse(null);
        FleetDocumentMetadata roadTax = fleetDocuments == null || vehicle == null
                ? null : fleetDocuments.currentVehicleRoadTax(vehicle.getVehicleId());
        return new DriverDetailsResponse(driver, vehicle, maskedIc(driver), ic, license, roadTax);
    }

    @Transactional
    public DriverDetailsResponse updateDriverDetails(Long id, DriverUpdateRequest request) {
        driverRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found."));
        if (request.isRegisterMissingVehicle() && request.getVehicle() == null) {
            throw new IllegalArgumentException("Vehicle details are required to complete registration.");
        }
        if (request.getVehicle() != null) {
            if (request.isRegisterMissingVehicle()) {
                vehicleService.createVehicleForDriver(toVehicle(request.getVehicle()), id);
            } else {
                vehicleService.updateAssignedVehicle(id, toVehicle(request.getVehicle()));
            }
        }
        Driver updated = updateDriver(id, request);
        if (request.getIcNumber() != null && !request.getIcNumber().isBlank()) {
            setEncryptedIc(updated, request.getIcNumber(), id);
            updated = driverRepository.save(updated);
        }
        return getDriver(id);
    }

    @Transactional
    public DriverDetailsResponse updateDriverWithDocuments(Long id, DriverUpdateRequest request,
            MultipartFile icDocument, MultipartFile licenseDocument,
            MultipartFile roadTaxDocument, Long adminId) {
        DriverDetailsResponse updated = updateDriverDetails(id, request);
        if (fleetDocuments != null) {
            if (icDocument != null && !icDocument.isEmpty()) {
                fleetDocuments.uploadDriverDocument(id, "DRIVER_IC", icDocument, adminId);
            }
            if (licenseDocument != null && !licenseDocument.isEmpty()) {
                fleetDocuments.uploadDriverDocument(id, "DRIVER_LICENSE", licenseDocument, adminId);
            }
            if (roadTaxDocument != null && !roadTaxDocument.isEmpty() && updated.vehicle() != null) {
                fleetDocuments.uploadVehicleRoadTax(
                        updated.vehicle().getVehicleId(), roadTaxDocument, adminId);
            }
        }
        return getDriver(id);
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
        existing.setDriverLicenseno(driver.getDriverLicenseno());
        if (driver.getDriverLicenseExpiry() != null && !driver.getDriverLicenseExpiry().isBlank()) {
            validateLicenseExpiry(driver.getDriverLicenseExpiry());
        }
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
        vehicle.setVehicleCategoryId(request.vehicleCategoryId());
        vehicle.setVehicleOwnership(request.vehicleOwnership());
        return vehicle;
    }

    private void setEncryptedIc(Driver driver, String rawIc, Long currentId) {
        String normalized = normalizeIc(rawIc);
        if (dataProtection == null || !dataProtection.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Encrypted driver identity storage is not configured.");
        }
        String lookup = dataProtection.icLookup(normalized);
        boolean duplicate = currentId == null
                ? driverRepository.findByDriverIcnoLookup(lookup).isPresent()
                : driverRepository.existsByDriverIcnoLookupAndDriverIdNot(lookup, currentId);
        if (duplicate) throw new IllegalArgumentException("This IC number already exists.");
        driver.setDriverIcnoCiphertext(dataProtection.encryptIc(normalized));
        driver.setDriverIcnoLookup(lookup);
        driver.setDriverIcno(null);
    }

    private String normalizeIc(String rawIc) {
        if (rawIc == null) throw new IllegalArgumentException("IC number is required.");
        String candidate = rawIc.trim().replaceAll("\\s+", "");
        if (!candidate.matches("\\d{12}|\\d{6}-\\d{2}-\\d{4}")) {
            throw new IllegalArgumentException("Enter a Malaysian IC number as XXXXXX-XX-XXXX or 12 digits.");
        }
        return candidate.replace("-", "");
    }

    private static String normalizeLegacyIc(String rawIc) {
        return rawIc.trim().replace("-", "").replaceAll("\\s+", "");
    }

    private void validateLicenseExpiry(String expiry) {
        try {
            java.time.LocalDate.parse(expiry);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Enter a valid driving licence expiry date.");
        }
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
