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

    @Column(name = "vehicle_luggagecapacity")
    private Integer vehicleLuggageCapacity;

    @Column(name = "max_large_luggage")
    private Integer maxLargeLuggage;

    @Column(name = "max_backpacks")
    private Integer maxBackpacks;

    public Integer getMaxLargeLuggage() { return maxLargeLuggage; }
    public void setMaxLargeLuggage(Integer value) { maxLargeLuggage = value; }
    public Integer getMaxBackpacks() { return maxBackpacks; }
    public void setMaxBackpacks(Integer value) { maxBackpacks = value; }

    public boolean fitsSeparateLuggage(int largeLuggage, int backpacks) {
        return (maxLargeLuggage == null || largeLuggage <= maxLargeLuggage)
                && (maxBackpacks == null || backpacks <= maxBackpacks);
    }

    public int passengerLimit() {
        int recordedCapacity = vehicleCapacity == null ? 0 : vehicleCapacity;
        String category = vehicleType == null ? "" : vehicleType.toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
        int categoryLimit = switch (category) {
            case "SEDAN" -> 3;
            case "MPV" -> 5;
            case "10SEATERVAN", "10SEATER", "VAN10" -> 8;
            case "14SEATERVAN", "14SEATER", "VAN14" -> 11;
            case "18SEATERVAN", "18SEATER", "VAN18" -> 15;
            default -> recordedCapacity;
        };
        return Math.min(recordedCapacity, categoryLimit);
    }

    @Column(name = "vehicle_roadtaxexpiry")
    private LocalDate vehicleRoadtaxexpiry;

    @Column(name = "vehicle_serviceexpiry")
    private LocalDate vehicleServiceexpiry;

    @Column(name = "vehicle_status")
    private String vehicleStatus;

    @Column(name = "driver_id", nullable = false, unique = true)
    private Long driverId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", insertable = false, updatable = false)
    private Driver driver;

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

    public Integer getVehicleLuggageCapacity() {
        return vehicleLuggageCapacity;
    }

    public void setVehicleLuggageCapacity(Integer vehicleLuggageCapacity) {
        this.vehicleLuggageCapacity = vehicleLuggageCapacity;
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
