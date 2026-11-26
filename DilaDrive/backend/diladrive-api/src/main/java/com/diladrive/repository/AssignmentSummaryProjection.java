package com.diladrive.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public interface AssignmentSummaryProjection {
    Long getAssignmentId();

    Long getBookingId();

    String getCustomer();
    String getCustomerPhone();

    LocalDate getTripDate();

    LocalTime getPickupTime();

    String getTripType();

    String getFlightNo();

    String getPickupAddress();

    String getDestinationAddress();

    Integer getPassengerCount();

    Integer getLuggageCount();
    Integer getSmallCount();
    Integer getMediumCount();
    Integer getLargeCount();
    Integer getLuggageUnits();

    String getLuggageSize();

    Long getDriverId();

    String getDriverName();

    Long getVehicleId();

    String getVehiclePlate();
    String getVehicleModel();
    String getVehicleCategory();

    String getAssignmentStatus();

    LocalDateTime getAssignmentDate();

    LocalDateTime getScheduledEndAt();
    Long getPreviousAssignmentId();
    String getReassignmentReason();
    String getBookingStatus();
    java.math.BigDecimal getBookingFare();
}
