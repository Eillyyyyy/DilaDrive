package com.diladrive.controller;

import com.diladrive.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class NotificationOwnershipTest {
    @Test
    void driverListAndMarkReadAreScopedToSession() throws Exception {
        NotificationService service = mock(NotificationService.class);
        var mvc = standaloneSetup(new NotificationController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        MockHttpSession driver = new MockHttpSession();
        driver.setAttribute("mobileRole", "DRIVER");
        driver.setAttribute("mobileUserId", 2L);

        mvc.perform(get("/api/notifications/driver/9").session(driver)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        mvc.perform(get("/api/notifications/driver/2").session(driver)).andExpect(status().isOk());
        verify(service).getDriverNotifications(2L);
        mvc.perform(patch("/api/notifications/5/read").session(driver)).andExpect(status().isOk());
        verify(service).markAsReadByOwner(5L, "DRIVER", 2L);
    }
}
