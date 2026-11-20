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
        String availability,
        String profileImage, String vehicleModel, String vehiclePlate,
        long totalTrips, long completedTrips, long upcomingTrips,
        String vehicleType, Integer vehicleCapacity, java.time.LocalDate vehicleRoadTaxExpiry,
        String vehicleStatus, boolean vehicleAssigned) {

    public static DriverProfileResponse from(Driver driver) {
        return from(driver, null, 0, 0, 0);
    }

    public static DriverProfileResponse from(Driver driver, com.diladrive.entity.Vehicle vehicle,
            long total, long completed, long upcoming) {
        return new DriverProfileResponse(
                driver.getDriverId(),
                driver.getDriverName(),
                driver.getDriverEmail(),
                driver.getDriverPhoneno(),
                driver.getDriverStatus(),
                driver.getDriverLicenseno(),
                driver.getDriverLicenseExpiry(),
                driver.getDriverType(),
                driver.getDriverAvailability(), driver.getProfileImage(),
                vehicle == null ? null : vehicle.getVehicleModel(),
                vehicle == null ? null : vehicle.getVehiclePlateno(), total, completed, upcoming,
                vehicle == null ? null : vehicle.getVehicleType(),
                vehicle == null ? null : vehicle.getVehicleCapacity(),
                vehicle == null ? null : vehicle.getVehicleRoadtaxexpiry(),
                vehicle == null ? null : vehicle.getVehicleStatus(), vehicle != null);
    }
}
