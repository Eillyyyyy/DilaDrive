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
                   d.driver_id AS "driverId",
                   d.driver_name AS "driverName",
                   v.vehicle_id AS "vehicleId",
                   v.vehicle_plateno AS "vehiclePlate",
                   a.assignment_status AS "assignmentStatus",
                   a.assignment_date AS "assignmentDate"
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
                   d.driver_id AS "driverId",
                   d.driver_name AS "driverName",
                   v.vehicle_id AS "vehicleId",
                   v.vehicle_plateno AS "vehiclePlate",
                   a.assignment_status AS "assignmentStatus",
                   a.assignment_date AS "assignmentDate"
            FROM driver_assignment a
            JOIN booking b ON b.booking_id = a.booking_id
            JOIN customer c ON c.cust_id = b.cust_id
            JOIN driver d ON d.driver_id = a.driver_id
            JOIN vehicle v ON v.vehicle_id = a.vehicle_id
            WHERE a.driver_id = :driverId
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
                   d.driver_id AS "driverId",
                   d.driver_name AS "driverName",
                   v.vehicle_id AS "vehicleId",
                   v.vehicle_plateno AS "vehiclePlate",
                   a.assignment_status AS "assignmentStatus",
                   a.assignment_date AS "assignmentDate"
            FROM driver_assignment a
            JOIN booking b ON b.booking_id = a.booking_id
            JOIN customer c ON c.cust_id = b.cust_id
            JOIN driver d ON d.driver_id = a.driver_id
            JOIN vehicle v ON v.vehicle_id = a.vehicle_id
            WHERE a.assignment_id = :id
            """, nativeQuery = true)
    Optional<AssignmentSummaryProjection> findAssignmentSummaryById(
            @Param("id") Long id);

    boolean existsByBookingBookingIdAndAssignmentStatusNotIn(
            Long bookingId,
            List<String> terminalStatuses);

    @Query("""
            select count(a) from Assignment a
            where upper(a.assignmentStatus) not in ('COMPLETED', 'CANCELLED')
            """)
    long countActiveAssignments();

    long countByAssignmentStatusIgnoreCase(String assignmentStatus);

    @EntityGraph(attributePaths = {"driver", "vehicle"})
    Optional<Assignment> findTopByBookingBookingIdOrderByAssignmentDateDesc(
            Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.assignmentId = :id")
    Optional<Assignment> findByIdForUpdate(@Param("id") Long id);
}
