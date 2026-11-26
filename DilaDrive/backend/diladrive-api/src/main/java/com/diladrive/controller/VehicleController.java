package com.diladrive.controller;

import com.diladrive.security.AdminSession;

import com.diladrive.dto.FleetDocumentMetadata;
import com.diladrive.dto.VehicleCategoryResponse;
import com.diladrive.dto.VehicleDetailsResponse;
import com.diladrive.entity.Vehicle;
import com.diladrive.service.FleetDocumentService;
import com.diladrive.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final FleetDocumentService fleetDocuments;

    @Autowired
    public VehicleController(
            VehicleService vehicleService, FleetDocumentService fleetDocuments) {
        this.vehicleService = vehicleService;
        this.fleetDocuments = fleetDocuments;
    }

    public VehicleController(VehicleService vehicleService) {
        this(vehicleService, null);
    }

    @GetMapping
    public List<Vehicle> getAllVehicles(HttpServletRequest request) {
        requireAdminId(request);
        return vehicleService.getAllVehicles();
    }

    @GetMapping("/{id}")
    public VehicleDetailsResponse getVehicle(@PathVariable Long id, HttpServletRequest request) {
        requireAdminId(request);
        Vehicle vehicle = vehicleService.getVehicle(id);
        FleetDocumentMetadata roadTax = fleetDocuments == null
                ? null : fleetDocuments.currentVehicleRoadTax(id);
        return new VehicleDetailsResponse(vehicle, roadTax);
    }

    @GetMapping("/categories")
    public List<VehicleCategoryResponse> getVehicleCategories(HttpServletRequest request) {
        requireAdminId(request);
        return vehicleService.getVehicleCategories().stream()
                .map(VehicleCategoryResponse::from).toList();
    }

    @PutMapping("/{id}")
    public Vehicle updateVehicle(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody com.diladrive.dto.AdminVehicleUpdateRequest vehicle,
            HttpServletRequest request) {
        requireAdminId(request);
        return vehicleService.updateVehicle(id, vehicle.toUpdate());
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public VehicleDetailsResponse updateVehicleWithRoadTax(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestPart("vehicle") com.diladrive.dto.AdminVehicleUpdateRequest vehicle,
            @RequestPart(value = "roadTaxDocument", required = false) MultipartFile roadTaxDocument,
            HttpServletRequest request) {
        Long adminId = requireAdminId(request);
        Vehicle updated = vehicleService.updateVehicle(id, vehicle.toUpdate());
        if (roadTaxDocument != null && !roadTaxDocument.isEmpty()) {
            fleetDocuments.uploadVehicleRoadTax(id, roadTaxDocument, adminId);
        }
        return new VehicleDetailsResponse(updated,
                fleetDocuments.currentVehicleRoadTax(id));
    }

    @GetMapping("/available")
    public List<Vehicle> getAvailableVehicles(HttpServletRequest request) {
        requireAdminId(request);
        return vehicleService.getAvailableVehicles();
    }

    private static Long requireAdminId(HttpServletRequest request) {
        return AdminSession.requireAdminId(request);
    }
}
