package com.diladrive.dto;

import com.diladrive.entity.Assignment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AssignmentResponse(
        Long assignmentId,
        Long bookingId,
        String customer,
        LocalDate tripDate,
        LocalTime pickupTime,
        String pickupAddress,
        String destinationAddress,
        Integer passengerCount,
        Long driverId,
        String driverName,
        Long vehicleId,
        String vehiclePlate,
        String assignmentStatus,
        LocalDateTime assignedAt,
        boolean notificationSent) {

    public static AssignmentResponse from(Assignment assignment) {
        var booking = assignment.getBooking();
        return new AssignmentResponse(
                assignment.getAssignmentId(),
                booking.getBookingId(),
                "Customer #" + booking.getCustId(),
                booking.getBookingTripdate(),
                booking.getBookingPickuptime(),
                booking.getBookingPickupaddress(),
                booking.getBookingDestinationaddress(),
                booking.getBookingPassengercount(),
                assignment.getDriver().getDriverId(),
                assignment.getDriver().getDriverName(),
                assignment.getVehicle().getVehicleId(),
                assignment.getVehicle().getVehiclePlateno(),
                assignment.getAssignmentStatus(),
                assignment.getAssignedAt(),
                true);
    }
}
