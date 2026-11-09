package com.diladrive.repository;

import com.diladrive.entity.Assignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findAllByOrderByAssignedAtDesc();

    boolean existsByBookingBookingIdAndAssignmentStatusNotIn(
            Long bookingId,
            List<String> terminalStatuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.assignmentId = :id")
    Optional<Assignment> findByIdForUpdate(@Param("id") Long id);
}
