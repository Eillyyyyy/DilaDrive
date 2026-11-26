package com.diladrive.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vehicle_category")
public class VehicleCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vehicle_category_id")
    private Long vehicleCategoryId;

    @Column(name = "category_key", nullable = false, unique = true)
    private String categoryKey;

    @Column(name = "display_name", nullable = false, unique = true)
    private String displayName;

    @Column(name = "max_passenger_capacity", nullable = false)
    private Integer maxPassengerCapacity;

    @Column(name = "boot_capacity_units", nullable = false)
    private Integer bootCapacityUnits;

    @Column(nullable = false)
    private boolean active;

    public Long getVehicleCategoryId() { return vehicleCategoryId; }
    public void setVehicleCategoryId(Long value) { vehicleCategoryId = value; }
    public String getCategoryKey() { return categoryKey; }
    public void setCategoryKey(String value) { categoryKey = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public Integer getMaxPassengerCapacity() { return maxPassengerCapacity; }
    public void setMaxPassengerCapacity(Integer value) { maxPassengerCapacity = value; }
    public Integer getBootCapacityUnits() { return bootCapacityUnits; }
    public void setBootCapacityUnits(Integer value) { bootCapacityUnits = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
}
