package com.diladrive.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "recipient_role", length = 20)
    private String recipientRole;

    @Column(name = "recipient_id")
    private Long recipientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "notification_title", nullable = false, length = 160)
    private String title;

    @Column(name = "notification_message", nullable = false, length = 2000)
    private String message;

    @Column(name = "notification_type", length = 50)
    private String notificationType;

    @Convert(converter = NotificationStatusConverter.class)
    @Column(name = "notification_status", nullable = false, length = 24)
    private boolean read;

    @Column(name = "notification_createdat", nullable = false)
    private LocalDateTime sentAt;

    @PrePersist
    void setDefaults() {
        if (driver != null) {
            recipientRole = "DRIVER";
            recipientId = driver.getDriverId();
        }
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public String getRecipientRole() {
        return recipientRole;
    }

    public void setRecipientRole(String recipientRole) {
        this.recipientRole = recipientRole;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
