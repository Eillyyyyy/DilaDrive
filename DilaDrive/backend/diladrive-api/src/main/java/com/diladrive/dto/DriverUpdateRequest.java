package com.diladrive.dto;
import com.diladrive.entity.Driver;
import jakarta.validation.Valid;
public class DriverUpdateRequest extends Driver {
    @Valid
    private VehicleCreateRequest vehicle;
    private boolean registerMissingVehicle;
    private String icNumber;
    public boolean isRegisterMissingVehicle() { return registerMissingVehicle; }
    public void setRegisterMissingVehicle(boolean value) { registerMissingVehicle = value; }
    public VehicleCreateRequest getVehicle() { return vehicle; }
    public void setVehicle(VehicleCreateRequest vehicle) { this.vehicle = vehicle; }
    public String getIcNumber() { return icNumber; }
    public void setIcNumber(String value) { icNumber = value; }
}
