package com.diladrive.controller;

import com.diladrive.security.AdminSession;

import com.diladrive.dto.DriverCreateRequest;
import com.diladrive.dto.DriverDetailsResponse;
import com.diladrive.dto.DriverListResponse;
import com.diladrive.dto.DriverUpdateRequest;
import com.diladrive.dto.DriverProfileResponse;
import com.diladrive.dto.DriverProfileUpdateRequest;
import com.diladrive.entity.Driver;
import com.diladrive.service.DriverService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(
            DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping("/me")
    public DriverProfileResponse getMyProfile(HttpServletRequest request) {
        return driverService.getProfile(requireDriverId(request));
    }

    @PutMapping("/me")
    public DriverProfileResponse updateMyProfile(
            @Valid @RequestBody DriverProfileUpdateRequest profile,
            HttpServletRequest request) {
        return driverService.updateProfile(requireDriverId(request), profile);
    }

    @PostMapping("/me/password")
    public ResponseEntity<?> changeMyPassword(@RequestBody com.diladrive.dto.ChangePasswordRequest body,
            HttpServletRequest request) {
        Long id = requireDriverId(request);
        try {
            driverService.changeDriverPassword(id, body);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping
    public List<DriverListResponse> getAllDrivers(HttpServletRequest request) {
        requireAdminId(request);
        return driverService.getAllDrivers().stream()
                .map(driver -> new DriverListResponse(driver, driverService.maskedIc(driver)))
                .toList();
    }

    @GetMapping("/available")
    public List<Driver> getAvailableDrivers(HttpServletRequest request) {
        requireAdminId(request);
        return driverService.getAvailableDrivers();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createDriver(
            @Valid @RequestBody DriverCreateRequest request,
            HttpServletRequest servletRequest) {
        requireAdminId(servletRequest);
        return ResponseEntity.badRequest()
                .body("Driving licence and road tax documents are required for new driver accounts.");
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createDriverWithDocuments(
            @Valid @RequestPart("driver") DriverCreateRequest request,
            @RequestPart(value = "icDocument", required = false) MultipartFile icDocument,
            @RequestPart("licenseDocument") MultipartFile licenseDocument,
            @RequestPart("roadTaxDocument") MultipartFile roadTaxDocument,
            HttpServletRequest servletRequest) {
        Long adminId = requireAdminId(servletRequest);
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(driverService.createDriverWithDocuments(
                            request, icDocument, licenseDocument, roadTaxDocument, adminId));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Driver email, IC number or vehicle registration number already exists.");
        }
    }

    // Form choices are API metadata, separate from persisted driver/vehicle records.
    @GetMapping("/options")
    public java.util.Map<String, List<String>> getFormOptions(HttpServletRequest request) {
        requireAdminId(request);
        return java.util.Map.of(
                "driverTypes", List.of("Company Driver", "Associate Driver"),
                "createAvailability", List.of("Available", "Busy", "Unavailable"),
                "availability", List.of("Available", "Busy", "Unavailable"),
                "accountStatuses", List.of("Active", "Disabled"),
                "vehicleStatuses", List.of("Available", "Maintenance", "Inactive"));
    }

    @PostMapping("/{id}/vehicle")
    public ResponseEntity<?> registerMissingVehicle(@PathVariable Long id,
            @Valid @RequestBody com.diladrive.dto.VehicleCreateRequest vehicle, HttpServletRequest request) {
        requireAdminId(request);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(driverService.registerMissingVehicle(id, vehicle));
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Vehicle plate or driver relationship already exists.");
        }
    }

    @GetMapping("/{id}")
    public DriverDetailsResponse getDriver(@PathVariable Long id, HttpServletRequest request) {
        requireAdminId(request);
        return driverService.getDriver(id);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDriver(
            @PathVariable Long id,
            @Valid @RequestBody DriverUpdateRequest driver,
            HttpServletRequest request) {
        requireAdminId(request);
        try {
            return ResponseEntity.ok(driverService.updateDriverDetails(id, driver));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Driver email or vehicle registration number already exists.");
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateDriverWithDocuments(
            @PathVariable Long id,
            @Valid @RequestPart("driver") DriverUpdateRequest driver,
            @RequestPart(value = "icDocument", required = false) MultipartFile icDocument,
            @RequestPart(value = "licenseDocument", required = false) MultipartFile licenseDocument,
            @RequestPart(value = "roadTaxDocument", required = false) MultipartFile roadTaxDocument,
            HttpServletRequest request) {
        Long adminId = requireAdminId(request);
        try {
            return ResponseEntity.ok(driverService.updateDriverWithDocuments(
                    id, driver, icDocument, licenseDocument, roadTaxDocument, adminId));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Driver email, IC number or vehicle registration number already exists.");
        }
    }

    private static Long requireDriverId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object role = session == null ? null : session.getAttribute("mobileRole");
        Object userId = session == null ? null : session.getAttribute("mobileUserId");
        if (!(userId instanceof Long id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Please sign in again.");
        }
        if (!"DRIVER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This profile is only available to drivers.");
        }
        return id;
    }

    private static Long requireAdminId(HttpServletRequest request) {
        return AdminSession.requireAdminId(request);
    }
}
