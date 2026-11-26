package com.diladrive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TripIncidentRequest(
        @NotNull Category category,
        @NotBlank @Size(max = MAX_DESCRIPTION) String description) {
    public static final int MAX_DESCRIPTION = 1970;

    public enum Category {
        BREAKDOWN, EMERGENCY, DELAY, OTHER;

        @com.fasterxml.jackson.annotation.JsonCreator
        public static Category fromJson(String value) {
            return valueOf(value);
        }
    }
}
