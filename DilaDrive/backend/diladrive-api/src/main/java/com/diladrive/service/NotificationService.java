package com.diladrive.service;

import com.diladrive.dto.NotificationResponse;
import com.diladrive.dto.NotificationCreateRequest;
import com.diladrive.entity.Admin;
import com.diladrive.entity.Notification;
import com.diladrive.repository.AdminRepository;
import com.diladrive.repository.DriverRepository;
import com.diladrive.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final DriverRepository driverRepository;
    private final AdminRepository adminRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            DriverRepository driverRepository,
            AdminRepository adminRepository) {
        this.notificationRepository = notificationRepository;
        this.driverRepository = driverRepository;
        this.adminRepository = adminRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getAdminNotifications(Long adminId) {
        return notificationRepository.findAllByOrderBySentAtDesc().stream()
                .filter(notification -> notification.getDriver() != null
                        || ("ADMIN".equalsIgnoreCase(notification.getRecipientRole())
                                && adminId.equals(notification.getRecipientId())))
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void notifyAdminsOfNewBooking(Long bookingId) {
        String reference = "BK-%06d".formatted(bookingId);
        for (Admin admin : adminRepository.findAll()) {
            if (!"ACTIVE".equalsIgnoreCase(admin.getStatus())) continue;
            Notification notification = new Notification();
            notification.setRecipientRole("ADMIN");
            notification.setRecipientId(admin.getId());
            notification.setTitle("New booking request");
            notification.setMessage(reference + " is waiting for verification.");
            notification.setNotificationType("BOOKING");
            notificationRepository.save(notification);
        }
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
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findByIdForUpdate(notificationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found."));
        notification.setRead(true);
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public NotificationResponse markAdminNotificationAsRead(Long notificationId, Long adminId) {
        Notification notification = notificationRepository.findByIdForUpdate(notificationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found."));
        boolean isAdminNotification = "ADMIN".equalsIgnoreCase(notification.getRecipientRole());
        if (isAdminNotification && !adminId.equals(notification.getRecipientId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found.");
        }
        notification.setRead(true);
        return NotificationResponse.from(notificationRepository.save(notification));
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
        boolean legacyDriverLinkMatches = "DRIVER".equalsIgnoreCase(recipientRole)
                && notification.getDriver() != null
                && recipientId.equals(notification.getDriver().getDriverId());
        if (!recipientColumnsMatch && !legacyDriverLinkMatches) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Notification not found.");
        }
        notification.setRead(true);
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
