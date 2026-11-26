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
    Optional<Driver> findByDriverIcnoLookup(String driverIcnoLookup);
    boolean existsByDriverIcnoLookupAndDriverIdNot(String driverIcnoLookup, Long driverId);

    List<Driver> findByDriverAvailabilityIgnoreCase(String availability);

    List<Driver> findByDriverAvailabilityIgnoreCaseAndDriverStatusIgnoreCase(
            String availability, String driverStatus);

    long countByDriverStatusIgnoreCase(String driverStatus);

    @Query(value = """
            select count(*) from driver d
            where upper(d.driver_status)='ACTIVE'
              and not exists (
                  select 1 from driver_assignment a join booking b using (booking_id)
                  where a.driver_id=d.driver_id
                    and upper(a.assignment_status) in ('OTW','WAITING','PICKUP','DROP')
                    and upper(b.booking_status) in ('CONFIRMED','ASSIGNED','OTW','WAITING','PICKUP','DROP'))
            """, nativeQuery = true)
    long countAvailableDrivers();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Driver d where d.driverId = :id")
    Optional<Driver> findByIdForUpdate(@Param("id") Long id);
}
