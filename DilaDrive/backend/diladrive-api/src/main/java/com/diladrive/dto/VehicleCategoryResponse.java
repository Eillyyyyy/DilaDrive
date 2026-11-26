package com.diladrive.dto;

import com.diladrive.entity.VehicleCategory;

public record VehicleCategoryResponse(Long vehicleCategoryId, String categoryKey,
        String displayName, Integer maxPassengerCapacity, Integer bootCapacityUnits) {
    public static VehicleCategoryResponse from(VehicleCategory category) {
        return new VehicleCategoryResponse(category.getVehicleCategoryId(),
                category.getCategoryKey(), category.getDisplayName(),
                category.getMaxPassengerCapacity(), category.getBootCapacityUnits());
    }
}
