package com.diladrive.controller;

import com.diladrive.dto.DriverProfileUpdateRequest;
import com.diladrive.service.DriverService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DriverControllerTest {

    private final DriverService driverService = mock(DriverService.class);
    private final DriverController controller = new DriverController(driverService);

    @Test
    void passwordChangeIsScopedToDriverSession() {
        var password = new com.diladrive.dto.ChangePasswordRequest();
        controller.changeMyPassword(password, driverRequest(42L));
        verify(driverService).changeDriverPassword(42L, password);
        assertThrows(ResponseStatusException.class, () -> controller.changeMyPassword(password, new MockHttpServletRequest()));
    }

    @Test
    void formOptionsRequireAdminAndProvideCreationChoices() {
        assertThrows(ResponseStatusException.class,
                () -> controller.getFormOptions(new MockHttpServletRequest()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("adminId", 3L);
        var options = controller.getFormOptions(request);
        assertEquals(java.util.List.of("Available", "Busy", "Unavailable"), options.get("createAvailability"));
        assertEquals(true, options.get("vehicleStatuses").contains("Maintenance"));
    }

    @Test
    void profileReadUsesDriverSessionId() {
        controller.getMyProfile(driverRequest(42L));

        verify(driverService).getProfile(42L);
    }

    @Test
    void profileUpdateUsesDriverSessionId() {
        DriverProfileUpdateRequest profile = new DriverProfileUpdateRequest(
                "Driver", "driver@example.com", "0123456789");

        controller.updateMyProfile(profile, driverRequest(42L));

        verify(driverService).updateProfile(42L, profile);
    }

    @Test
    void profileRejectsCustomerSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", 42L);
        request.getSession().setAttribute("mobileRole", "CUSTOMER");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.getMyProfile(request));

        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void driverManagementRejectsNonAdminSession() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.getAllDrivers(new MockHttpServletRequest()));

        assertEquals(401, error.getStatusCode().value());
    }

    private static MockHttpServletRequest driverRequest(Long driverId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileUserId", driverId);
        request.getSession().setAttribute("mobileRole", "DRIVER");
        return request;
    }
}
