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

    public VehicleService(
            VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
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

    @Transactional(propagation = Propagation.MANDATORY)
    public Vehicle createVehicleForDriver(Vehicle vehicle, Long driverId) {
        if (vehicle == null || driverId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Vehicle and driver details are required.");
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
        vehicle.setVehicleRoadtaxexpiry(request.getVehicleRoadtaxexpiry());
        vehicle.setVehicleServiceexpiry(request.getVehicleServiceexpiry());
        vehicle.setVehicleStatus(status);
        return vehicleRepository.save(vehicle);
    }

    public List<Vehicle> getAvailableVehicles() {
        return vehicleRepository.findByVehicleStatusIgnoreCase("AVAILABLE");
    }

    @Transactional
    public void deleteVehicle(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Vehicle not found."));
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
            case "IN_USE", "UNAVAILABLE" -> "UNAVAILABLE";
            case "MAINTENANCE" -> "MAINTENANCE";
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported vehicle status.");
        };
    }
}