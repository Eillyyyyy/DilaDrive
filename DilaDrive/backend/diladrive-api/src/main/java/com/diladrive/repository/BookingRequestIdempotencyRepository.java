package com.diladrive.repository;

import com.diladrive.entity.BookingRequestIdempotency;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface BookingRequestIdempotencyRepository
        extends JpaRepository<BookingRequestIdempotency, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO booking_request_idempotency
                (customer_id, idempotency_key, request_hash, created_at)
            VALUES (:customerId, :key, :requestHash, CURRENT_TIMESTAMP)
            ON CONFLICT (customer_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("customerId") Long customerId,
            @Param("key") String key, @Param("requestHash") String requestHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BookingRequestIdempotency r "
            + "where r.customerId = :customerId and r.idempotencyKey = :key")
    Optional<BookingRequestIdempotency> findForUpdate(
            @Param("customerId") Long customerId, @Param("key") String key);
}
