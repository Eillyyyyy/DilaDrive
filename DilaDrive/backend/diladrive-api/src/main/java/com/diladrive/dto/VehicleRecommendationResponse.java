package com.diladrive.dto;

public record VehicleRecommendationResponse(
        boolean available,
        String message,
        RecommendedVehicle vehicle) {

    public record RecommendedVehicle(
            Long vehicleId,
            String model,
            String vehicleType,
            Integer passengerCapacity,
            Integer luggageCapacity) {
        @com.fasterxml.jackson.annotation.JsonProperty("bootCapacityUnits")
        public Integer bootCapacityUnits() { return luggageCapacity; }
    }
}
