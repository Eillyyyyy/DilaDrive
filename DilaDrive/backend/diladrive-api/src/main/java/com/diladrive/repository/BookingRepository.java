package com.diladrive.repository;

import com.diladrive.entity.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository
        extends JpaRepository<Booking, Long> {

    List<Booking> findByBookingTripdateAndBookingStatusIgnoreCase(
            LocalDate tripDate,
            String bookingStatus
    );

    List<Booking> findByBookingTripdateAndBookingPickuptimeAndBookingStatusIgnoreCase(
            LocalDate tripDate,
            LocalTime pickupTime,
            String bookingStatus
    );

    @Query("""
            select b from Booking b
            where b.custId = :customerId
            order by b.bookingCreatedat desc nulls last, b.bookingId desc
            """)
    List<Booking> findCustomerBookingsNewestFirst(
            @Param("customerId") Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.bookingId = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(b) from Booking b
            where upper(b.bookingStatus) in :statuses
            """)
    long countByStatusInIgnoreCase(@Param("statuses") List<String> statuses);
}
