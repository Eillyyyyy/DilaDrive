package com.diladrive.service;

import com.diladrive.entity.Vehicle;
import com.diladrive.repository.VehicleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final com.diladrive.repository.AssignmentRepository assignmentRepository;

    public VehicleService(
            VehicleRepository vehicleRepository, com.diladrive.repository.AssignmentRepository assignmentRepository) {
        this.vehicleRepository = vehicleRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Vehicle getVehicle(Long vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Vehicle not found."));
    }

    @Transactional(readOnly = true)
    public Vehicle getAssignedVehicle(Long driverId) {
        List<Vehicle> vehicles = vehicleRepository.findByDriverId(driverId);
        if (vehicles.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Driver has multiple assigned vehicles. Update vehicles individually.");
        }
        return vehicles.isEmpty() ? null : vehicles.get(0);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Vehicle updateAssignedVehicle(Long driverId, Vehicle request) {
        Vehicle assigned = getAssignedVehicle(driverId);
        if (assigned == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No vehicle is assigned to this driver.");
        }
        Vehicle locked = vehicleRepository.findByIdForUpdate(assigned.getVehicleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found."));
        if (!driverId.equals(locked.getDriverId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle assignment has changed.");
        }
        return updateVehicle(locked.getVehicleId(), request);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Vehicle createVehicleForDriver(Vehicle vehicle, Long driverId) {
        if (vehicle == null || driverId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Vehicle and driver details are required.");
        }

        if (!vehicleRepository.findByDriverId(driverId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This driver already has a linked vehicle.");
        }
        String plate = normalizeRequired(vehicle.getVehiclePlateno(), "Registration number");
        String model = normalizeRequired(vehicle.getVehicleModel(), "Vehicle model");
        String type = normalizeRequired(vehicle.getVehicleType(), "Vehicle type");
        if (vehicle.getVehicleCapacity() == null || vehicle.getVehicleCapacity() < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Passenger capacity must be a whole number greater than zero.");
        }
        String status = normalizeStatus(vehicle.getVehicleStatus());

        if (vehicleRepository.existsByVehiclePlatenoIgnoreCase(plate)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This registration number already exists.");
        }

        vehicle.setVehiclePlateno(plate);
        vehicle.setVehicleModel(model);
        vehicle.setVehicleType(type);
        vehicle.setVehicleStatus(status);
        vehicle.setDriverId(driverId);
        return vehicleRepository.save(vehicle);
    }

    @Transactional
    public Vehicle updateVehicle(Long vehicleId, Vehicle request) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Vehicle details are required.");
        }

        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Vehicle not found."));
        if (assignmentRepository.existsByVehicleVehicleIdAndAssignmentStatusNotIn(vehicleId, List.of("COMPLETED", "CANCELLED"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle details cannot be changed while booking assignments are active.");
        }
        if (request.getDriverId() != null && !request.getDriverId().equals(vehicle.getDriverId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The linked driver cannot be changed here.");
        }
        String plate = normalizeRequired(request.getVehiclePlateno(), "Registration number");
        String type = normalizeRequired(request.getVehicleType(), "Vehicle type");
        Integer capacity = request.getVehicleCapacity();
        if (capacity == null || capacity < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Passenger capacity must be a whole number greater than zero.");
        }
        String status = normalizeStatus(request.getVehicleStatus());

        if (vehicleRepository.existsByVehiclePlatenoIgnoreCaseAndVehicleIdNot(
                plate, vehicleId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This registration number already exists.");
        }

        vehicle.setVehiclePlateno(plate);
        vehicle.setVehicleModel(normalizeOptional(request.getVehicleModel()));
        vehicle.setVehicleType(type);
        vehicle.setVehicleCapacity(capacity);
        if ((request.getMaxLargeLuggage() != null && request.getMaxLargeLuggage() < 0)
                || (request.getMaxBackpacks() != null && request.getMaxBackpacks() < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle luggage limits must be zero or greater.");
        }
        if (request.getMaxLargeLuggage() != null) vehicle.setMaxLargeLuggage(request.getMaxLargeLuggage());
        if (request.getMaxBackpacks() != null) vehicle.setMaxBackpacks(request.getMaxBackpacks());
        if (request.getVehicleLuggageCapacity() != null) {
            if (request.getVehicleLuggageCapacity() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Luggage capacity must be zero or greater.");
            }
            vehicle.setVehicleLuggageCapacity(request.getVehicleLuggageCapacity());
        }
        vehicle.setVehicleRoadtaxexpiry(request.getVehicleRoadtaxexpiry());
        vehicle.setVehicleServiceexpiry(request.getVehicleServiceexpiry());
        vehicle.setVehicleStatus(status);
        return vehicleRepository.save(vehicle);
    }

    public List<Vehicle> getAvailableVehicles() {
        return vehicleRepository.findAll().stream().filter(v ->
                java.util.Set.of("ACTIVE","AVAILABLE").contains(String.valueOf(v.getVehicleStatus()).toUpperCase(Locale.ROOT))).toList();
    }

    @Transactional
    public void deleteVehicle(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Vehicle not found."));
        if (vehicle.getDriverId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A current linked vehicle cannot be deleted.");
        }
        vehicleRepository.delete(vehicle);
    }

    private String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, field + " is required.");
        }
        String normalized = value.trim();
        if ("Registration number".equals(field)) {
            normalized = normalized.toUpperCase(Locale.ROOT);
            if (normalized.length() > 20) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Registration number must be 20 characters or fewer.");
            }
        } else if ("Vehicle model".equals(field) && normalized.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Vehicle model must be 100 characters or fewer.");
        } else if ("Vehicle type".equals(field) && normalized.length() > 60) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Vehicle type must be 60 characters or fewer.");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Vehicle status is required.");
        }
        return switch (value.trim().toUpperCase(Locale.ROOT).replace(' ', '_')) {
            case "AVAILABLE" -> "AVAILABLE";
            case "ACTIVE" -> "ACTIVE";
            case "IN_USE", "UNAVAILABLE", "INACTIVE" -> "INACTIVE";
            case "MAINTENANCE" -> "MAINTENANCE";
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported vehicle status.");
        };
    }
}
