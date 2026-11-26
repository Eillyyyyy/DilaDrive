package com.diladrive.dto;
import com.diladrive.entity.Driver;
import com.diladrive.entity.Vehicle;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
public record DriverDetailsResponse(@JsonUnwrapped Driver driver, Vehicle vehicle,
        String driverIcnoMasked, FleetDocumentMetadata icDocument,
        FleetDocumentMetadata licenseDocument, FleetDocumentMetadata roadTaxDocument) {}
