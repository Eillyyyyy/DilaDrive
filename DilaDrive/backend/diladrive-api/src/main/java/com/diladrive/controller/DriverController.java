package com.diladrive.controller;

import com.diladrive.dto.DriverCreateRequest;
import com.diladrive.dto.DriverProfileResponse;
import com.diladrive.dto.DriverProfileUpdateRequest;
import com.diladrive.entity.Driver;
import com.diladrive.service.DriverService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @GetMapping
    public List<Driver> getAllDrivers(HttpServletRequest request) {
        requireAdminId(request);
        return driverService.getAllDrivers();
    }

    @GetMapping("/available")
    public List<Driver> getAvailableDrivers(HttpServletRequest request) {
        requireAdminId(request);
        return driverService.getAvailableDrivers();
    }

    @PostMapping
    public ResponseEntity<?> createDriver(
            @Valid @RequestBody DriverCreateRequest request,
            HttpServletRequest servletRequest) {
        requireAdminId(servletRequest);
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(driverService.createDriver(request));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Driver email or vehicle registration number already exists.");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDriver(
            @PathVariable Long id,
            @RequestBody Driver driver,
            HttpServletRequest request) {
        requireAdminId(request);
        try {
            return ResponseEntity.ok(driverService.updateDriver(id, driver));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDriver(
            @PathVariable Long id,
            HttpServletRequest request) {
        requireAdminId(request);
        try {
            driverService.deleteDriver(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Driver cannot be deleted while referenced by bookings or assignments.");
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
        var session = request.getSession(false);
        Object adminId = session == null ? null : session.getAttribute("adminId");
        if (adminId instanceof Long id) {
            return id;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Please sign in as an admin.");
    }
}
