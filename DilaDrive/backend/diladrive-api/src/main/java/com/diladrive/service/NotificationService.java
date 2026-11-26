package com.diladrive.service;

import com.diladrive.dto.NotificationResponse;
import com.diladrive.dto.NotificationCreateRequest;
import com.diladrive.dto.NotificationTarget;
import com.diladrive.entity.Notification;
import com.diladrive.repository.DriverRepository;
import com.diladrive.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class NotificationService {

    @org.springframework.beans.factory.annotation.Autowired
    private BookingPushService bookingPush;
    private final NotificationRepository notificationRepository;
    private final DriverRepository driverRepository;
    private static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");

    public NotificationService(
            NotificationRepository notificationRepository,
            DriverRepository driverRepository) {
        this.notificationRepository = notificationRepository;
        this.driverRepository = driverRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getAdminNotifications(Long adminId) {
        return notificationRepository.findByRecipientRoleIgnoreCaseAndRecipientIdOrderBySentAtDesc(
                        "ADMIN", adminId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    public record InboxPage(List<NotificationResponse> items, long total, List<String> types) {}

    @Transactional(readOnly = true)
    public InboxPage inbox(Long adminId, int page, int size, Boolean read, String type, String search) {
        return pageResponse(notificationRepository.inbox(adminId, read, type, search,
                PageRequest.of(page, size)), notificationRepository.inboxTypes(adminId));
    }

    @Transactional(readOnly = true)
    public InboxPage sentDriverNotifications(int page, int size) {
        return pageResponse(notificationRepository.findByDriverIsNotNullOrderBySentAtDescNotificationIdDesc(
                PageRequest.of(page, size)), List.of());
    }

    private InboxPage pageResponse(Page<Notification> page, List<String> types) {
        return new InboxPage(page.getContent().stream().map(NotificationResponse::from).toList(),
                page.getTotalElements(), types);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long adminId) {
        return notificationRepository.countByRecipientRoleIgnoreCaseAndRecipientIdAndReadFalse("ADMIN", adminId);
    }

    @Transactional
    public long markAllRead(Long adminId) {
        notificationRepository.markAdminInboxRead(adminId, LocalDateTime.now(MALAYSIA));
        return unreadCount(adminId);
    }

    @Transactional
    public void notifyAdminsOfNewBooking(Long bookingId) {
        String reference = "BK-%06d".formatted(bookingId);
        notifyAdmins("New booking request", reference + " is waiting for verification and assignment.",
                "BOOKING", NotificationTarget.BOOKING, bookingId, "booking-created:" + bookingId);
    }

    @Transactional
    public void notifyAdmins(String title, String message, String type,
            NotificationTarget target, Long entityId, String eventKey) {
        notificationRepository.insertAdminEvent(title, message, type, target.name(), entityId,
                target.route(entityId), eventKey, LocalDateTime.now(MALAYSIA));
    }

    @Transactional
    public NotificationResponse createNotification(NotificationCreateRequest request) {
        var driver = driverRepository.findById(request.driverId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Driver not found."));
        Notification notification = new Notification();
        notification.setDriver(driver);
        notification.setTitle(request.title().trim());
        notification.setMessage(request.message().trim());
        notification.setNotificationType(
                request.notificationType() == null
                        ? "GENERAL"
                        : request.notificationType().trim().toUpperCase(Locale.ROOT));
        if (bookingPush != null) {
            bookingPush.notifyAfterCommit("DRIVER", driver.getDriverId(),
                    notification.getTitle(), notification.getMessage());
        }
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getDriverNotifications(Long driverId) {
        if (!driverRepository.existsById(driverId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found.");
        }
        return notificationRepository.findByDriverDriverIdOrderBySentAtDesc(driverId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getCustomerNotifications(Long customerId) {
        return notificationRepository
                .findByRecipientRoleIgnoreCaseAndRecipientIdOrderBySentAtDesc(
                        "CUSTOMER", customerId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public NotificationResponse createCustomerNotification(
            Long customerId,
            String title,
            String message,
            String notificationType) {
        Notification notification = new Notification();
        notification.setRecipientRole("CUSTOMER");
        notification.setRecipientId(customerId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNotificationType(notificationType);
        if (bookingPush != null && !isCustomerPaymentNotice(notificationType, message)) {
            String pushMessage = "ACCOUNT_VERIFICATION".equals(notificationType)
                    ? "Open DilaDrive to view your account verification result." : message;
            bookingPush.notifyAfterCommit("CUSTOMER", customerId, title, pushMessage);
        }
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    private static boolean isCustomerPaymentNotice(String notificationType, String message) {
        String type = notificationType == null ? "" : notificationType.trim().toUpperCase(java.util.Locale.ROOT);
        return type.startsWith("PAYMENT") || type.startsWith("RECEIPT")
                || ("DOCUMENT".equals(type) && message != null && message.startsWith("REC-"));
    }

    boolean hasCustomerNotification(Long customerId, String title, String message) {
        return notificationRepository.existsByRecipientRoleAndRecipientIdAndTitleAndMessage(
                "CUSTOMER", customerId, title, message);
    }

    @Transactional
    public NotificationResponse markAdminNotificationAsRead(Long notificationId, Long adminId) {
        return markAsReadByOwner(notificationId, "ADMIN", adminId);
    }

    @Transactional
    public NotificationResponse markAsReadByOwner(
            Long notificationId,
            String recipientRole,
            Long recipientId) {
        Notification notification = notificationRepository.findByIdForUpdate(notificationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found."));
        boolean recipientColumnsMatch = recipientRole.equalsIgnoreCase(
                notification.getRecipientRole())
                && recipientId.equals(notification.getRecipientId());
        boolean legacyDriverLinkMatches = notification.getRecipientRole() == null
                && "DRIVER".equalsIgnoreCase(recipientRole)
                && notification.getDriver() != null
                && recipientId.equals(notification.getDriver().getDriverId());
        if (!recipientColumnsMatch && !legacyDriverLinkMatches) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Notification not found.");
        }
        notification.markRead(LocalDateTime.now(MALAYSIA));
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public void deleteNotification(Long notificationId) {
        if (!notificationRepository.existsById(notificationId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Notification not found.");
        }
        notificationRepository.deleteById(notificationId);
    }
}
