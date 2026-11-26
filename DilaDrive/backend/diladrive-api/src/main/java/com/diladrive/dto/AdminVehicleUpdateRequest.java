package com.diladrive.dto;

import com.diladrive.entity.Vehicle;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record AdminVehicleUpdateRequest(
        @Size(max = 120) String vehicleModel,
        @NotBlank @Size(max = 30) String vehiclePlateno,
        @Size(max = 80) String vehicleType,
        @Positive Long vehicleCategoryId,
        @Min(1) Integer vehicleCapacity,
        @Min(0) Integer vehicleLuggageCapacity,
        LocalDate vehicleRoadtaxexpiry,
        LocalDate vehicleServiceexpiry,
        @NotBlank @Size(max = 30) String vehicleStatus,
        @Size(max = 30) String vehicleOwnership) {

    public Vehicle toUpdate() {
        var vehicle = new Vehicle();
        vehicle.setVehicleModel(vehicleModel);
        vehicle.setVehiclePlateno(vehiclePlateno);
        vehicle.setVehicleType(vehicleType);
        vehicle.setVehicleCategoryId(vehicleCategoryId);
        vehicle.setVehicleCapacity(vehicleCapacity);
        vehicle.setVehicleLuggageCapacity(vehicleLuggageCapacity);
        vehicle.setVehicleRoadtaxexpiry(vehicleRoadtaxexpiry);
        vehicle.setVehicleServiceexpiry(vehicleServiceexpiry);
        vehicle.setVehicleStatus(vehicleStatus);
        vehicle.setVehicleOwnership(vehicleOwnership);
        return vehicle;
    }
}
