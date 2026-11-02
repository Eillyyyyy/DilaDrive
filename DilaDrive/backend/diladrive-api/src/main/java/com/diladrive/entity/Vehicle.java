package com.diladrive.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Column(name = "vehicle_model")
    private String vehicleModel;

    @Column(name = "vehicle_capacity")
    private Integer vehicleCapacity;

    @Column(name = "vehicle_status")
    private String vehicleStatus;

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

    public Integer getVehicleCapacity() {
        return vehicleCapacity;
    }

    public void setVehicleCapacity(Integer vehicleCapacity) {
        this.vehicleCapacity = vehicleCapacity;
    }

    public String getVehicleStatus() {
        return vehicleStatus;
    }

    public void setVehicleStatus(String vehicleStatus) {
        this.vehicleStatus = vehicleStatus;
    }
}