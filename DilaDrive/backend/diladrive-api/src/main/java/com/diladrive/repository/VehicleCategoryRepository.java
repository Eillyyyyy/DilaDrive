package com.diladrive.repository;

import com.diladrive.entity.VehicleCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleCategoryRepository extends JpaRepository<VehicleCategory, Long> {
    List<VehicleCategory> findByActiveTrueOrderByDisplayNameAsc();
    Optional<VehicleCategory> findByCategoryKeyIgnoreCaseAndActiveTrue(String categoryKey);
    Optional<VehicleCategory> findByDisplayNameIgnoreCaseAndActiveTrue(String displayName);
}
