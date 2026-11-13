package com.diladrive.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record BookingFareRequest(@NotNull @Positive BigDecimal tripFare) {
}
