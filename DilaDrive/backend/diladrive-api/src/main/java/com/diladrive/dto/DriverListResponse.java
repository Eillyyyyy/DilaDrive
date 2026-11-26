package com.diladrive.dto;

import com.diladrive.entity.Driver;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record DriverListResponse(@JsonUnwrapped Driver driver, String driverIcnoMasked) {}
