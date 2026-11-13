package com.diladrive.controller;

import com.diladrive.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationControllerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationController controller =
            new NotificationController(notificationService);

    @Test
    void customerListUsesTheAuthenticatedCustomerId() {
        MockHttpServletRequest request = customerRequest(42L);

        controller.getMyCustomerNotifications(request);

        verify(notificationService).getCustomerNotifications(42L);
    }

    @Test
    void customerListRejectsDriverSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", 17L);
        request.getSession().setAttribute("mobileRole", "DRIVER");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.getMyCustomerNotifications(request));

        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void markingReadPassesSessionOwnerToService() {
        MockHttpServletRequest request = customerRequest(42L);

        controller.markAsRead(5L, request);

        verify(notificationService).markAsReadByOwner(5L, "CUSTOMER", 42L);
    }

    private MockHttpServletRequest customerRequest(Long customerId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", customerId);
        request.getSession().setAttribute("mobileRole", "CUSTOMER");
        return request;
    }
}
