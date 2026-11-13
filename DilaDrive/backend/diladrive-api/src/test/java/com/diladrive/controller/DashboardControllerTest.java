package com.diladrive.controller;

import com.diladrive.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DashboardControllerTest {
    private final DashboardService service = mock(DashboardService.class);
    private final DashboardController controller = new DashboardController(service);

    @Test
    void statisticsRequireAdminSession() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.getStats(new MockHttpServletRequest()));
        assertEquals(401, error.getStatusCode().value());
        verifyNoInteractions(service);
    }

    @Test
    void statisticsUseExistingAdminSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("adminId", 3L);

        controller.getStats(request);

        verify(service).getStats();
    }
}
