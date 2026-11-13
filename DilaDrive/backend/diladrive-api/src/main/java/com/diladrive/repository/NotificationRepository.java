package com.diladrive.repository;

import com.diladrive.entity.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByOrderBySentAtDesc();

    List<Notification> findByDriverDriverIdOrderBySentAtDesc(Long driverId);

    List<Notification> findByRecipientRoleIgnoreCaseAndRecipientIdOrderBySentAtDesc(
            String recipientRole,
            Long recipientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.notificationId = :id")
    Optional<Notification> findByIdForUpdate(@Param("id") Long id);
}
