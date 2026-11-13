package com.diladrive.repository;

import com.diladrive.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByOrderBySubmittedAtDesc();
    List<Payment> findByBookingCustIdOrderBySubmittedAtDesc(Long customerId);
    boolean existsByBookingBookingIdAndPaymentStatus(Long bookingId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentId = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Query("select coalesce(sum(p.amount), 0) from Payment p "
            + "where p.booking.bookingId = :bookingId and p.paymentStatus = 'VERIFIED'")
    BigDecimal sumVerifiedAmount(@Param("bookingId") Long bookingId);
}
