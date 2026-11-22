package com.diladrive.security;

import com.diladrive.repository.DriverRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;

public final class DriverSessionGuard implements HandlerInterceptor {
    private final DriverRepository drivers;
    private final ObjectMapper json;

    public DriverSessionGuard(DriverRepository drivers, ObjectMapper json) {
        this.drivers = drivers;
        this.json = json;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        var session = request.getSession(false);
        if ("OPTIONS".equals(request.getMethod()) || session == null
                || !"DRIVER".equals(session.getAttribute("mobileRole"))
                || !(session.getAttribute("mobileUserId") instanceof Long driverId)) {
            return true;
        }
        if (drivers.findById(driverId)
                .filter(driver -> "ACTIVE".equalsIgnoreCase(driver.getDriverStatus()))
                .isPresent()) {
            return true;
        }

        session.removeAttribute("mobileUserId");
        session.removeAttribute("mobileRole");
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), Map.of(
                "code", "DRIVER_ACCOUNT_DEACTIVATED",
                "message", "Your driver account has been deactivated. Please contact the administrator."));
        return false;
    }
}
