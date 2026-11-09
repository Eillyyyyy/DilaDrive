package com.diladrive.service;

import com.diladrive.dto.NotificationResponse;
import com.diladrive.dto.NotificationCreateRequest;
import com.diladrive.entity.Notification;
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

    public NotificationService(
            NotificationRepository notificationRepository,
            DriverRepository driverRepository) {
        this.notificationRepository = notificationRepository;
        this.driverRepository = driverRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getAllNotifications() {
        return notificationRepository.findAllByOrderBySentAtDesc().stream()
                .filter(notification -> notification.getDriver() != null)
                .map(NotificationResponse::from)
                .toList();
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

    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findByIdForUpdate(notificationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found."));
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
