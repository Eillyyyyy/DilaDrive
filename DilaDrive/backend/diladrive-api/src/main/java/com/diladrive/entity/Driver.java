package com.diladrive.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "driver")
public class Driver {

    @Id
    @Column(name = "driver_id")
    private Long driverId;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "driver_email")
    private String driverEmail;

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getDriverEmail() {
        return driverEmail;
    }

    public void setDriverEmail(String driverEmail) {
        this.driverEmail = driverEmail;
    }
}