package com.diladrive.dto;

import com.diladrive.entity.Driver;

public record DriverProfileResponse(
        Long id,
        String name,
        String email,
        String phone,
        String status,
        String licenseNumber,
        String licenseExpiry,
        String driverType,
        String availability) {

    public static DriverProfileResponse from(Driver driver) {
        return new DriverProfileResponse(
                driver.getDriverId(),
                driver.getDriverName(),
                driver.getDriverEmail(),
                driver.getDriverPhoneno(),
                driver.getDriverStatus(),
                driver.getDriverLicenseno(),
                driver.getDriverLicenseExpiry(),
                driver.getDriverType(),
                driver.getDriverAvailability());
    }
}
