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
        LocalDateTime sentAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getDriver().getDriverId(),
                notification.getDriver().getDriverName(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getNotificationType(),
                notification.isRead(),
                notification.getSentAt());
    }
}
