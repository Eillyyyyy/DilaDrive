package com.diladrive.dto;

import com.diladrive.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        Long driverId,
        String driverName,
        String title,
        String message,
        String notificationType,
        boolean read,
        LocalDateTime sentAt,
        String recipientRole,
        Long recipientId) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getDriver() == null
                        ? null
                        : notification.getDriver().getDriverId(),
                notification.getDriver() == null
                        ? null
                        : notification.getDriver().getDriverName(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getNotificationType(),
                notification.isRead(),
                notification.getSentAt(),
                notification.getRecipientRole(),
                notification.getRecipientId());
    }
}
