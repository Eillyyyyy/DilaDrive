package com.diladrive.dto;

public record BookingAvailability(
        int availableDrivers,
        int availableVehicles,
        boolean available
) {
}
