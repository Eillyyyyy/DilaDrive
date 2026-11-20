package com.diladrive.repository;

import com.diladrive.entity.Assignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @Query(value = """
            SELECT a.assignment_id AS "assignmentId",
                   b.booking_id AS "bookingId",
                   c.cust_name AS "customer",
                   b.booking_tripdate AS "tripDate",
                   b.booking_pickuptime AS "pickupTime",
                   b.booking_triptype AS "tripType",
                   b.booking_flightno AS "flightNo",
                   b.booking_pickupaddress AS "pickupAddress",
                   b.booking_destinationaddress AS "destinationAddress",
                   b.booking_passengercount AS "passengerCount",
                   b.booking_luggagecount AS "luggageCount",
                   b.booking_luggagesize AS "luggageSize",
                   b.small_count AS "smallCount", b.medium_count AS "mediumCount",
                   b.large_count AS "largeCount", b.luggage_units AS "luggageUnits",
                   d.driver_id AS "driverId",
                   d.driver_name AS "driverName",
                   v.vehicle_id AS "vehicleId",
                   v.vehicle_plateno AS "vehiclePlate",
                   a.assignment_status AS "assignmentStatus",
                   a.assignment_date AS "assignmentDate",
                   a.scheduled_end_at AS "scheduledEndAt"
            FROM driver_assignment a
            JOIN booking b ON b.booking_id = a.booking_id
            JOIN customer c ON c.cust_id = b.cust_id
            JOIN driver d ON d.driver_id = a.driver_id
            JOIN vehicle v ON v.vehicle_id = a.vehicle_id
            ORDER BY a.assignment_id DESC
            """, nativeQuery = true)
    List<AssignmentSummaryProjection> findAllAssignmentSummaries();

    @Query(value = """
            SELECT a.assignment_id AS "assignmentId",
                   b.booking_id AS "bookingId",
                   c.cust_name AS "customer",
                   b.booking_tripdate AS "tripDate",
                   b.booking_pickuptime AS "pickupTime",
                   b.booking_triptype AS "tripType",
                   b.booking_flightno AS "flightNo",
                   b.booking_pickupaddress AS "pickupAddress",
                   b.booking_destinationaddress AS "destinationAddress",
                   b.booking_passengercount AS "passengerCount",
                   b.booking_luggagecount AS "luggageCount",
                   b.booking_luggagesize AS "luggageSize",
                   b.small_count AS "smallCount", b.medium_count AS "mediumCount",
                   b.large_count AS "largeCount", b.luggage_units AS "luggageUnits",
                   d.driver_id AS "driverId",
                   d.driver_name AS "driverName",
                   v.vehicle_id AS "vehicleId",
                   v.vehicle_plateno AS "vehiclePlate",
                   a.assignment_status AS "assignmentStatus",
                   a.assignment_date AS "assignmentDate",
                   a.scheduled_end_at AS "scheduledEndAt"
            FROM driver_assignment a
            JOIN booking b ON b.booking_id = a.booking_id
            JOIN customer c ON c.cust_id = b.cust_id
            JOIN driver d ON d.driver_id = a.driver_id
            JOIN vehicle v ON v.vehicle_id = a.vehicle_id
            WHERE a.driver_id = :driverId AND upper(a.assignment_status) <> 'SUPERSEDED'
              AND upper(b.booking_status) NOT IN ('PENDING','PENDING_VERIFICATION','REJECTED')
            ORDER BY b.booking_tripdate DESC, a.assignment_id DESC
            """, nativeQuery = true)
    List<AssignmentSummaryProjection> findAssignmentSummariesByDriverId(
            @Param("driverId") Long driverId);

    @Query(value = """
            SELECT a.assignment_id AS "assignmentId",
                   b.booking_id AS "bookingId",
                   c.cust_name AS "customer",
                   b.booking_tripdate AS "tripDate",
                   b.booking_pickuptime AS "pickupTime",
                   b.booking_triptype AS "tripType",
                   b.booking_flightno AS "flightNo",
                   b.booking_pickupaddress AS "pickupAddress",
                   b.booking_destinationaddress AS "destinationAddress",
                   b.booking_passengercount AS "passengerCount",
                   b.booking_luggagecount AS "luggageCount",
                   b.booking_luggagesize AS "luggageSize",
                   b.small_count AS "smallCount", b.medium_count AS "mediumCount",
                   b.large_count AS "largeCount", b.luggage_units AS "luggageUnits",
                   d.driver_id AS "driverId",
                   d.driver_name AS "driverName",
                   v.vehicle_id AS "vehicleId",
                   v.vehicle_plateno AS "vehiclePlate",
                   a.assignment_status AS "assignmentStatus",
                   a.assignment_date AS "assignmentDate",
                   a.scheduled_end_at AS "scheduledEndAt"
            FROM driver_assignment a
            JOIN booking b ON b.booking_id = a.booking_id
            JOIN customer c ON c.cust_id = b.cust_id
            JOIN driver d ON d.driver_id = a.driver_id
            JOIN vehicle v ON v.vehicle_id = a.vehicle_id
            WHERE a.assignment_id = :id
            """, nativeQuery = true)
    Optional<AssignmentSummaryProjection> findAssignmentSummaryById(
            @Param("id") Long id);

    @Query("select (count(a) > 0) from Assignment a where a.booking.bookingId = :bookingId and upper(a.assignmentStatus) not in :terminalStatuses")
    boolean existsByBookingBookingIdAndAssignmentStatusNotIn(
            Long bookingId,
            List<String> terminalStatuses);

    @Query("""
            select count(a) from Assignment a
            where upper(a.assignmentStatus) not in ('COMPLETED', 'CANCELLED', 'SUPERSEDED')
              and upper(a.booking.bookingStatus) in ('CONFIRMED','ASSIGNED','OTW','WAITING','PICKUP','DROP')
            """)
    long countActiveAssignments();

    @Query("select count(a) from Assignment a where a.driver.driverId=:driverId and upper(a.assignmentStatus)<>'SUPERSEDED'")
    long countByDriverDriverId(@Param("driverId") Long driverId);
    long countByDriverDriverIdAndAssignmentStatusIgnoreCase(Long driverId, String status);

    @Query("select count(a) from Assignment a where a.driver.driverId = :driverId and upper(a.assignmentStatus) = 'ASSIGNED' and (a.booking.bookingTripdate > :date or (a.booking.bookingTripdate = :date and a.booking.bookingPickuptime >= :time))")
    long countUpcomingDriverTrips(@Param("driverId") Long driverId,
            @Param("date") java.time.LocalDate date, @Param("time") java.time.LocalTime time);

    long countByAssignmentStatusIgnoreCase(String assignmentStatus);

    boolean existsByDriverDriverIdAndAssignmentStatusNotIn(Long driverId, List<String> statuses);

    boolean existsByVehicleVehicleIdAndAssignmentStatusNotIn(Long vehicleId, List<String> statuses);

    @EntityGraph(attributePaths = {"booking", "driver", "vehicle"})
    @Query("select a from Assignment a where (a.driver.driverId = :driverId or a.vehicle.vehicleId = :vehicleId) and upper(a.assignmentStatus) in ('ASSIGNED', 'OTW', 'WAITING', 'PICKUP', 'DROP') and upper(a.booking.bookingStatus) in ('CONFIRMED','ASSIGNED','OTW','WAITING','PICKUP','DROP')")
    List<Assignment> findResourceReservations(@Param("driverId") Long driverId, @Param("vehicleId") Long vehicleId);

    @EntityGraph(attributePaths = {"driver", "vehicle"})
    Optional<Assignment> findTopByBookingBookingIdOrderByAssignmentDateDesc(
            Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.assignmentId = :id")
    Optional<Assignment> findByIdForUpdate(@Param("id") Long id);
}
