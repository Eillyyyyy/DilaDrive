package com.diladrive.repository;

import com.diladrive.entity.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByRecipientRoleAndRecipientIdAndTitleAndMessage(
            String recipientRole, Long recipientId, String title, String message);

    @Query("""
            select n from Notification n
            where upper(n.recipientRole) = 'ADMIN' and n.recipientId = :id
              and (:read is null or n.read = :read)
              and (:type = '' or upper(n.notificationType) = :type)
              and (:search = '' or locate(lower(:search), lower(n.title)) > 0
                   or locate(lower(:search), lower(n.message)) > 0)
            order by n.sentAt desc, n.notificationId desc
            """)
    Page<Notification> inbox(@Param("id") Long id, @Param("read") Boolean read,
            @Param("type") String type, @Param("search") String search, Pageable page);

    Page<Notification> findByDriverIsNotNullOrderBySentAtDescNotificationIdDesc(Pageable page);

    long countByRecipientRoleIgnoreCaseAndRecipientIdAndReadFalse(String role, Long id);

    @Query("""
            select distinct upper(n.notificationType) from Notification n
            where upper(n.recipientRole) = 'ADMIN' and n.recipientId = :id
              and n.notificationType is not null
            order by upper(n.notificationType)
            """)
    List<String> inboxTypes(@Param("id") Long id);

    @Modifying
    @Query("""
            update Notification n set n.read = true, n.readAt = :now
            where upper(n.recipientRole) = 'ADMIN' and n.recipientId = :id and n.read = false
            """)
    int markAdminInboxRead(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);

    @Modifying
    @Query(value = """
            insert into notification (recipient_role, recipient_id, notification_title,
                notification_message, notification_type, notification_status, notification_createdat,
                entity_type, entity_id, target_route, event_key)
            select 'ADMIN', admin_id, :title, :message, :type, 'UNREAD', :now,
                :entityType, :entityId, :route, :eventKey
            from admin where upper(admin_status) = 'ACTIVE'
            order by admin_id
            on conflict (recipient_role, recipient_id, event_key) do nothing
            """, nativeQuery = true)
    int insertAdminEvent(@Param("title") String title, @Param("message") String message,
            @Param("type") String type, @Param("entityType") String entityType,
            @Param("entityId") Long entityId, @Param("route") String route,
            @Param("eventKey") String eventKey, @Param("now") java.time.LocalDateTime now);

    @Query("""
            select n from Notification n left join n.driver d
            where (upper(n.recipientRole) = 'DRIVER' and n.recipientId = :driverId)
               or (n.recipientRole is null and d.driverId = :driverId)
            order by n.sentAt desc
            """)
    List<Notification> findByDriverDriverIdOrderBySentAtDesc(@Param("driverId") Long driverId);

    List<Notification> findByRecipientRoleIgnoreCaseAndRecipientIdOrderBySentAtDesc(
            String recipientRole,
            Long recipientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.notificationId = :id")
    Optional<Notification> findByIdForUpdate(@Param("id") Long id);
}
