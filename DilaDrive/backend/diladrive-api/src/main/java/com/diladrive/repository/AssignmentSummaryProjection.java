package com.diladrive.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public interface AssignmentSummaryProjection {
    Long getAssignmentId();

    Long getBookingId();

    String getCustomer();

    LocalDate getTripDate();

    LocalTime getPickupTime();

    String getTripType();

    String getFlightNo();

    String getPickupAddress();

    String getDestinationAddress();

    Integer getPassengerCount();

    Integer getLuggageCount();

    String getLuggageSize();

    Long getDriverId();

    String getDriverName();

    Long getVehicleId();

    String getVehiclePlate();

    String getAssignmentStatus();

    LocalDateTime getAssignmentDate();
}
