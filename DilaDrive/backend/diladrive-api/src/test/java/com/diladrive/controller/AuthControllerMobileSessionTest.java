package com.diladrive.controller;

import com.diladrive.dto.MobileLoginRequest;
import com.diladrive.dto.MobileLoginResponse;
import com.diladrive.service.MobileAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerMobileSessionTest {

    @Test
    void successfulMobileLoginCreatesRoleScopedSession() {
        MobileAuthService mobileAuthService = mock(MobileAuthService.class);
        when(mobileAuthService.login(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(new MobileLoginResponse(
                        42L,
                        "Test Customer",
                        "customer@example.com",
                        null,
                        "VERIFIED",
                        "CUSTOMER")));
        AuthController controller = new AuthController(mobileAuthService);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MobileLoginRequest request = new MobileLoginRequest();
        request.setRole("CUSTOMER");

        var response = controller.mobileLogin(request, servletRequest);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(42L, servletRequest.getSession(false).getAttribute("mobileUserId"));
        assertEquals("CUSTOMER", servletRequest.getSession(false).getAttribute("mobileRole"));
    }
}
