package com.diladrive.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleCategoryCapacityTest {
    @ParameterizedTest
    @CsvSource({"Sedan,3", "MPV,5", "10-Seater Van,8", "14-Seater Van,11", "18-Seater Van,15"})
    void appliesScopeLimitToKnownLegacyVehicles(String category, int limit) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleType(category);
        vehicle.setVehicleCapacity(limit + 1);
        assertEquals(limit, vehicle.passengerLimit());
        vehicle.setVehicleCapacity(limit - 1);
        assertEquals(limit - 1, vehicle.passengerLimit());
    }

    @Test
    void passengerCapacityCannotExceedConfiguredCategoryLimit() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleType("10-Seater Van");
        vehicle.setVehicleCapacity(12);

        assertEquals(8, vehicle.passengerLimit());

        vehicle.setVehicleCapacity(6);
        assertEquals(6, vehicle.passengerLimit());
    }

    @Test
    void knownLegacyCategoryStillCapsCapacityWhenNoCategoryRowIsLoaded() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleType("18-Seater Van");
        vehicle.setVehicleCapacity(18);

        assertEquals(15, vehicle.passengerLimit());
    }
}
