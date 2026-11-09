package com.diladrive.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Column(name = "vehicle_model")
    private String vehicleModel;

    @Column(name = "vehicle_plateno")
    private String vehiclePlateno;

    @Column(name = "vehicle_type")
    private String vehicleType;

    @Column(name = "vehicle_capacity")
    private Integer vehicleCapacity;

    @Column(name = "vehicle_roadtaxexpiry")
    private LocalDate vehicleRoadtaxexpiry;

    @Column(name = "vehicle_serviceexpiry")
    private LocalDate vehicleServiceexpiry;

    @Column(name = "vehicle_status")
    private String vehicleStatus;

    @Column(name = "driver_id")
    private Long driverId;

    public Vehicle() {
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    public void setVehicleModel(String vehicleModel) {
        this.vehicleModel = vehicleModel;
    }

    public String getVehiclePlateno() {
        return vehiclePlateno;
    }

    public void setVehiclePlateno(String vehiclePlateno) {
        this.vehiclePlateno = vehiclePlateno;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public Integer getVehicleCapacity() {
        return vehicleCapacity;
    }

    public void setVehicleCapacity(Integer vehicleCapacity) {
        this.vehicleCapacity = vehicleCapacity;
    }

    public LocalDate getVehicleRoadtaxexpiry() {
        return vehicleRoadtaxexpiry;
    }

    public void setVehicleRoadtaxexpiry(LocalDate vehicleRoadtaxexpiry) {
        this.vehicleRoadtaxexpiry = vehicleRoadtaxexpiry;
    }

    public LocalDate getVehicleServiceexpiry() {
        return vehicleServiceexpiry;
    }

    public void setVehicleServiceexpiry(LocalDate vehicleServiceexpiry) {
        this.vehicleServiceexpiry = vehicleServiceexpiry;
    }

    public String getVehicleStatus() {
        return vehicleStatus;
    }

    public void setVehicleStatus(String vehicleStatus) {
        this.vehicleStatus = vehicleStatus;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }
}