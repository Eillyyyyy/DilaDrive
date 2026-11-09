package com.diladrive.dto;

public class DashboardStatsDTO {

    private long customers;
    private long drivers;
    private long vehicles;
    private long bookings;

    public DashboardStatsDTO(
            long customers,
            long drivers,
            long vehicles,
            long bookings) {

        this.customers = customers;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.bookings = bookings;
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
}