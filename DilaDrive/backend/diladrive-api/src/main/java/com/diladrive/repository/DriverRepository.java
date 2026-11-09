package com.diladrive.repository;

import com.diladrive.entity.Driver;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DriverRepository
        extends JpaRepository<Driver, Long> {

    Optional<Driver> findByDriverEmailIgnoreCase(String email);

    List<Driver> findByDriverAvailabilityIgnoreCase(String availability);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Driver d where d.driverId = :id")
    Optional<Driver> findByIdForUpdate(@Param("id") Long id);
}