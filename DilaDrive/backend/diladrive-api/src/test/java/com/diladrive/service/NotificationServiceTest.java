package com.diladrive.service;

import com.diladrive.entity.Notification;
import com.diladrive.entity.NotificationStatusConverter;
import com.diladrive.entity.Driver;
import com.diladrive.entity.Admin;
import com.diladrive.repository.AdminRepository;
import com.diladrive.repository.DriverRepository;
import com.diladrive.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final AdminRepository admins = mock(AdminRepository.class);
    private final NotificationService service = new NotificationService(
            notifications, mock(DriverRepository.class), admins);

    @Test
    void newBookingCreatesNotificationForEachActiveAdmin() {
        Admin active = new Admin();
        active.setId(7L);
        active.setStatus("ACTIVE");
        Admin inactive = new Admin();
        inactive.setId(8L);
        inactive.setStatus("INACTIVE");
        when(admins.findAll()).thenReturn(List.of(active, inactive));

        service.notifyAdminsOfNewBooking(42L);

        verify(notifications, times(1)).save(argThat(notification ->
                "ADMIN".equals(notification.getRecipientRole())
                        && Long.valueOf(7L).equals(notification.getRecipientId())
                        && "New booking request".equals(notification.getTitle())
                        && "BK-000042 is waiting for verification."
                                .equals(notification.getMessage())
                        && "BOOKING".equals(notification.getNotificationType())));
        verify(notifications, never()).save(argThat(notification ->
                Long.valueOf(8L).equals(notification.getRecipientId())));
    }

    @Test
    void mapsLegacyNotificationStatusToBooleanReadState() {
        NotificationStatusConverter converter = new NotificationStatusConverter();

        assertTrue(converter.convertToEntityAttribute("READ"));
        assertFalse(converter.convertToEntityAttribute("UNREAD"));
        assertFalse(converter.convertToEntityAttribute(null));
        assertEquals("READ", converter.convertToDatabaseColumn(true));
        assertEquals("UNREAD", converter.convertToDatabaseColumn(false));
    }

    @Test
    void ownerCanMarkTheirNotificationRead() {
        Notification notification = customerNotification(42L);
        when(notifications.findByIdForUpdate(5L)).thenReturn(Optional.of(notification));
        when(notifications.save(notification)).thenReturn(notification);

        var response = service.markAsReadByOwner(5L, "CUSTOMER", 42L);

        assertTrue(response.read());
        verify(notifications).save(notification);
    }

    @Test
    void anotherCustomerCannotMarkNotificationRead() {
        Notification notification = customerNotification(42L);
        when(notifications.findByIdForUpdate(5L)).thenReturn(Optional.of(notification));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.markAsReadByOwner(5L, "CUSTOMER", 43L));

        assertEquals(404, error.getStatusCode().value());
        assertFalse(notification.isRead());
        verify(notifications, never()).save(any());
    }

    @Test
    void driverCanMarkLegacyDriverLinkedNotificationRead() {
        Driver driver = new Driver();
        driver.setDriverId(12L);
        Notification notification = new Notification();
        notification.setNotificationId(8L);
        notification.setDriver(driver);
        notification.setTitle("Assignment update");
        notification.setMessage("A trip was assigned.");
        when(notifications.findByIdForUpdate(8L)).thenReturn(Optional.of(notification));
        when(notifications.save(notification)).thenReturn(notification);

        var response = service.markAsReadByOwner(8L, "DRIVER", 12L);

        assertTrue(response.read());
        verify(notifications).save(notification);
    }

    @Test
    void anotherDriverCannotMarkLegacyDriverLinkedNotificationRead() {
        Driver driver = new Driver();
        driver.setDriverId(12L);
        Notification notification = new Notification();
        notification.setNotificationId(8L);
        notification.setDriver(driver);
        notification.setTitle("Assignment update");
        notification.setMessage("A trip was assigned.");
        when(notifications.findByIdForUpdate(8L)).thenReturn(Optional.of(notification));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.markAsReadByOwner(8L, "DRIVER", 13L));

        assertEquals(404, error.getStatusCode().value());
        assertFalse(notification.isRead());
        verify(notifications, never()).save(any());
    }

    private Notification customerNotification(Long customerId) {
        Notification notification = new Notification();
        notification.setNotificationId(5L);
        notification.setRecipientRole("CUSTOMER");
        notification.setRecipientId(customerId);
        notification.setTitle("Booking update");
        notification.setMessage("A real booking status update.");
        return notification;
    }
}
