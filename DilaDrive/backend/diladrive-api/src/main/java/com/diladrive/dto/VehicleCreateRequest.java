package com.diladrive.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.io.IOException;

public record VehicleCreateRequest(
        @NotBlank @Size(max = 100) String vehicleModel,
        @NotBlank @Size(max = 20) String vehiclePlateno,
        @Size(max = 60) String vehicleType,
        @NotNull(message = "Select a category with a passenger capacity.")
        @JsonDeserialize(using = CapacityNumber.class)
        @Min(value = 1, message = "Passenger capacity must be at least one.") Integer vehicleCapacity,
        LocalDate vehicleRoadtaxexpiry,
        LocalDate vehicleServiceexpiry,
        @NotBlank String vehicleStatus,
        @NotNull(message = "Select a category with a luggage capacity.")
        @JsonDeserialize(using = CapacityNumber.class)
        @Min(value = 0, message = "Luggage capacity cannot be negative.") Integer vehicleLuggageCapacity,
        Long vehicleCategoryId,
        String vehicleOwnership) {
    public VehicleCreateRequest(String vehicleModel, String vehiclePlateno, String vehicleType,
            Integer vehicleCapacity, LocalDate vehicleRoadtaxexpiry, LocalDate vehicleServiceexpiry,
            String vehicleStatus, Integer vehicleLuggageCapacity) {
        this(vehicleModel, vehiclePlateno, vehicleType, vehicleCapacity, vehicleRoadtaxexpiry,
                vehicleServiceexpiry, vehicleStatus, vehicleLuggageCapacity, null, null);
    }

    public static final class CapacityNumber extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Integer.class, "Capacity must be a whole JSON number.");
            }
            return parser.getIntValue();
        }
    }
}
