package com.diladrive.dto;

public class DashboardStatsDTO {

    private long customers;
    private long drivers;
    private long vehicles;
    private long bookings;
    private long totalBookings;
    private long pendingBookings;
    private long activeDrivers;
    private long availableDrivers;
    private long availableVehicles;
    private long activeAssignments;
    private long completedTrips;

    public DashboardStatsDTO(
            long customers,
            long drivers,
            long vehicles,
            long bookings,
            long pendingBookings,
            long activeDrivers,
            long availableDrivers,
            long availableVehicles,
            long activeAssignments,
            long completedTrips) {

        this.customers = customers;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.bookings = bookings;
        this.totalBookings = bookings;
        this.pendingBookings = pendingBookings;
        this.activeDrivers = activeDrivers;
        this.availableDrivers = availableDrivers;
        this.availableVehicles = availableVehicles;
        this.activeAssignments = activeAssignments;
        this.completedTrips = completedTrips;
    }

    public long getCustomers() {
        return customers;
    }

    public long getDrivers() {
        return drivers;
    }

    public long getVehicles() {
        return vehicles;
    }

    public long getBookings() {
        return bookings;
    }

    public long getTotalBookings() {
        return totalBookings;
    }

    public long getPendingBookings() {
        return pendingBookings;
    }

    public long getActiveDrivers() {
        return activeDrivers;
    }

    public long getAvailableDrivers() {
        return availableDrivers;
    }

    public long getAvailableVehicles() {
        return availableVehicles;
    }

    public long getActiveAssignments() {
        return activeAssignments;
    }

    public long getCompletedTrips() {
        return completedTrips;
    }
}
