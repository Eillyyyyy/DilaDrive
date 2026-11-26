package com.diladrive.repository;

import com.diladrive.entity.FleetDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FleetDocumentRepository extends JpaRepository<FleetDocument, Long> {
    Optional<FleetDocument> findFirstByDriverDriverIdAndDocumentTypeAndCurrentTrue(
            Long driverId, String documentType);

    Optional<FleetDocument> findFirstByVehicleVehicleIdAndDocumentTypeAndCurrentTrue(
            Long vehicleId, String documentType);

    List<FleetDocument> findByDriverDriverIdAndCurrentTrue(Long driverId);
    List<FleetDocument> findByVehicleVehicleIdAndCurrentTrue(Long vehicleId);
}
