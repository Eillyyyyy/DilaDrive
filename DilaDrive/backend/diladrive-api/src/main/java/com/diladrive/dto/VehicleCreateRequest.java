package com.diladrive.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record VehicleCreateRequest(
        @NotBlank @Size(max = 100) String vehicleModel,
        @NotBlank @Size(max = 20) String vehiclePlateno,
        @NotBlank @Size(max = 60) String vehicleType,
        @NotNull @Min(1) Integer vehicleCapacity,
        LocalDate vehicleRoadtaxexpiry,
        LocalDate vehicleServiceexpiry,
        @NotBlank String vehicleStatus,
        @NotNull @Min(0) Integer vehicleLuggageCapacity) {
}
