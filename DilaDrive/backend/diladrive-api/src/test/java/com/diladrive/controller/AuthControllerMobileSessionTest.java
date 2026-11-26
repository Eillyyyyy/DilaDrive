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
        String previousSession = servletRequest.getSession().getId();
        MobileLoginRequest request = new MobileLoginRequest();
        request.setRole("CUSTOMER");

        var response = controller.mobileLogin(request, servletRequest);

        assertEquals(200, response.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertNotEquals(previousSession, servletRequest.getSession().getId());
        assertEquals(42L, servletRequest.getSession(false).getAttribute("mobileUserId"));
        assertEquals("CUSTOMER", servletRequest.getSession(false).getAttribute("mobileRole"));
    }

    @Test
    void throttlesByNormalizedAccountAndIp() {
        MobileAuthService auth = mock(MobileAuthService.class);
        when(auth.login(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        AuthController controller = new AuthController(auth);
        for (int attempt = 0; attempt < 5; attempt++) {
            var response = login(controller, "Customer@example.com", "192.0.2." + attempt);
            assertEquals(401, response.getStatusCode().value());
            assertEquals("Invalid Email or Password", response.getBody());
        }
        assertEquals(429, login(controller, "customer@example.com", "192.0.2.6").getStatusCode().value());

        for (int attempt = 0; attempt < 30; attempt++) {
            assertEquals(401, login(controller, "test" + attempt + "@example.com", "192.0.2.10").getStatusCode().value());
        }
        assertEquals(429, login(controller, "other@example.com", "192.0.2.10").getStatusCode().value());
    }

    private static org.springframework.http.ResponseEntity<?> login(AuthController controller, String email, String ip) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr(ip);
        MobileLoginRequest request = new MobileLoginRequest();
        request.setEmail(email);
        request.setRole("CUSTOMER");
        return controller.mobileLogin(request, servletRequest);
    }
}
