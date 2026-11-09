package com.diladrive.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@Table(name = "driver")
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "driver_id")
    private Long driverId;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "driver_email")
    private String driverEmail;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "driver_password")
    private String driverPassword;

    @Column(name = "driver_phoneno")
    private String driverPhoneno;

    @Column(name = "driver_status")
    private String driverStatus;

    @Column(name = "driver_icno")
    private String driverIcno;

    @Column(name = "driver_licenseno")
    private String driverLicenseno;

    @Column(name = "driver_license_expiry")
    private String driverLicenseExpiry;

    @Column(name = "driver_type")
    private String driverType;

    @Column(name = "driver_availability")
    private String driverAvailability;

    public Driver() {
    }

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

    public String getDriverPassword() {
        return driverPassword;
    }

    public void setDriverPassword(String driverPassword) {
        this.driverPassword = driverPassword;
    }

    public String getDriverPhoneno() {
        return driverPhoneno;
    }

    public void setDriverPhoneno(String driverPhoneno) {
        this.driverPhoneno = driverPhoneno;
    }

    public String getDriverStatus() {
        return driverStatus;
    }

    public void setDriverStatus(String driverStatus) {
        this.driverStatus = driverStatus;
    }

    public String getDriverIcno() {
        return driverIcno;
    }

    public void setDriverIcno(String driverIcno) {
        this.driverIcno = driverIcno;
    }

    public String getDriverLicenseno() {
        return driverLicenseno;
    }

    public void setDriverLicenseno(String driverLicenseno) {
        this.driverLicenseno = driverLicenseno;
    }

    public String getDriverLicenseExpiry() {
        return driverLicenseExpiry;
    }

    public void setDriverLicenseExpiry(String driverLicenseExpiry) {
        this.driverLicenseExpiry = driverLicenseExpiry;
    }

    public String getDriverType() {
        return driverType;
    }

    public void setDriverType(String driverType) {
        this.driverType = driverType;
    }

    public String getDriverAvailability() {
        return driverAvailability;
    }

    public void setDriverAvailability(String driverAvailability) {
        this.driverAvailability = driverAvailability;
    }
}