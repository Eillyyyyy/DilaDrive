package com.diladrive.dto;

import com.diladrive.entity.Vehicle;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record VehicleDetailsResponse(@JsonUnwrapped Vehicle vehicle,
        FleetDocumentMetadata roadTaxDocument) {}
