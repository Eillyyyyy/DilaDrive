package com.diladrive.controller;

import com.diladrive.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class VehicleControllerTest {
    private final VehicleService service = mock(VehicleService.class);
    private final VehicleController controller = new VehicleController(service);

    @Test
    void vehicleListRequiresAdminSession() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.getAllVehicles(new MockHttpServletRequest()));
        assertEquals(401, error.getStatusCode().value());
        verifyNoInteractions(service);
    }

    @Test
    void vehicleListUsesExistingAdminSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("adminId", 3L);

        controller.getAllVehicles(request);

        verify(service).getAllVehicles();
    }

    @Test
    void driverSessionCannotReadAdminFleetData() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", 12L);
        request.getSession().setAttribute("mobileRole", "DRIVER");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.getAvailableVehicles(request));

        assertEquals(401, error.getStatusCode().value());
        verifyNoInteractions(service);
    }
}
