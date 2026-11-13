package com.diladrive.controller;

import com.diladrive.entity.Vehicle;
import com.diladrive.service.VehicleService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(
            VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<Vehicle> getAllVehicles(HttpServletRequest request) {
        requireAdminId(request);
        return vehicleService.getAllVehicles();
    }

    @GetMapping("/{id}")
    public Vehicle getVehicle(@PathVariable Long id, HttpServletRequest request) {
        requireAdminId(request);
        return vehicleService.getVehicle(id);
    }

    @PutMapping("/{id}")
    public Vehicle updateVehicle(
            @PathVariable Long id,
            @RequestBody Vehicle vehicle,
            HttpServletRequest request) {
        requireAdminId(request);
        return vehicleService.updateVehicle(id, vehicle);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVehicle(
            @PathVariable Long id, HttpServletRequest request) {
        requireAdminId(request);
        try {
            vehicleService.deleteVehicle(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Vehicle cannot be deleted while referenced by bookings or assignments.");
        }
    }

    @GetMapping("/available")
    public List<Vehicle> getAvailableVehicles(HttpServletRequest request) {
        requireAdminId(request);
        return vehicleService.getAvailableVehicles();
    }

    private static Long requireAdminId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object adminId = session == null ? null : session.getAttribute("adminId");
        if (adminId instanceof Long id) return id;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Please sign in as an admin.");
    }
}
