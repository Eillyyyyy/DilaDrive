package com.diladrive.repository;

import com.diladrive.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByVehicleCapacityGreaterThanEqualAndVehicleStatus(
            Integer vehicleCapacity,
            String vehicleStatus
    );
}