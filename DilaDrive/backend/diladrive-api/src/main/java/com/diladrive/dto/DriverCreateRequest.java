package com.diladrive.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DriverCreateRequest(
        @NotBlank @Size(max = 120) String driverName,
        @NotBlank @Email @Size(max = 160) String driverEmail,
        @NotBlank @Size(min = 8) String driverPassword,
        @NotBlank @Size(max = 30) String driverPhoneno,
        String driverIcno,
        String driverLicenseno,
        String driverLicenseExpiry,
        String driverType,
        String driverStatus,
        String driverAvailability,
        @NotNull @Valid VehicleCreateRequest vehicle) {
}
