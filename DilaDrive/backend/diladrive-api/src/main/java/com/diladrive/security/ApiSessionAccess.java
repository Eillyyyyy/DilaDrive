package com.diladrive.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;

public final class ApiSessionAccess implements HandlerInterceptor {
    private final com.diladrive.repository.AdminRepository admins;

    public ApiSessionAccess() {
        this(null);
    }

    public ApiSessionAccess(com.diladrive.repository.AdminRepository admins) {
        this.admins = admins;
    }
    private static final Set<String> PUBLIC = Set.of(
            "POST /api/auth/login", "POST /api/auth/mobile/login",
            "POST /api/auth/mobile/register", "POST /api/auth/logout");

    private static final Set<String> CUSTOMER = Set.of(
            "GET /api/customers/me", "PUT /api/customers/me",
            "GET /api/locations/search", "GET /api/locations/reverse",
            "POST /api/bookings/estimate", "POST /api/bookings/availability",
            "POST /api/bookings/route", "POST /api/bookings/customer",
            "POST /api/bookings/customer/complete", "POST /api/bookings/customer/road-distance-review",
            "PUT /api/bookings/{id}/customer/complete", "PUT /api/bookings/{id}/customer",
            "PATCH /api/bookings/{id}/customer/schedule",
            "GET /api/bookings/{id}/timeline", "GET /api/bookings/mine", "GET /api/bookings/mine/{id}",
            "GET /api/bookings/availability/recommendation", "GET /api/bookings/fare-estimate",
            "POST /api/bookings/{id}/cancel",
            "POST /api/bookings/{bookingId}/fare-proposals/{proposalId}/accept",
            "GET /api/notifications/customer/mine", "GET /api/documents/customer/mine",
            "GET /api/documents/customer/{id}/pdf", "GET /api/documents/customer/receipts",
            "GET /api/documents/customer/receipts/{id}/pdf", "GET /api/payments/mine",
            "POST /api/payments/bookings/{bookingId}");

    private static final Set<String> DRIVER = Set.of(
            "GET /api/drivers/me", "PUT /api/drivers/me", "POST /api/drivers/me/password",
            "GET /api/assignments/mine", "GET /api/assignments/mine/{id}",
            "POST /api/assignments/{id}/incidents", "POST /api/assignments/{id}/photos" );

    private static final Set<String> CUSTOMER_OR_ADMIN = Set.of(
            "GET /api/bookings/availability", "GET /api/payments/{paymentId}/proof",
            "GET /api/bookings/{bookingId}/fare-proposals");

    private static final Set<String> DRIVER_OR_ADMIN = Set.of(
            "PATCH /api/assignments/{id}/status", "GET /api/assignments/{id}/timeline",
            "GET /api/assignments/{id}/photos", "GET /api/assignments/{id}/photos/{photoId}/content",
            "GET /api/notifications/driver/{driverId}", "POST /api/payments/bookings/{bookingId}/cash");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod) || "OPTIONS".equals(request.getMethod())) {
            return true;
        }

        String route = request.getMethod() + " " + request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (PUBLIC.contains(route)) {
            return true;
        }

        var session = request.getSession(false);
        Object mobileId = session == null ? null : session.getAttribute("mobileUserId");
        Object role = session == null ? null : session.getAttribute("mobileRole");
        boolean customer = mobileId instanceof Long id && id > 0 && "CUSTOMER".equals(role);
        boolean driver = mobileId instanceof Long id && id > 0 && "DRIVER".equals(role);
        boolean admin = session != null && session.getAttribute("adminId") != null;
        if (admin && (customer || driver)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        }

        if (!customer && !driver && !admin) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        }
        if (admin && admins != null && request.getAttribute("activeAdminChecked") == null) {
            Long id = AdminSession.requireAdminId(request);
            if (admins.findById(id).filter(account -> "ACTIVE".equalsIgnoreCase(account.getStatus())).isEmpty()) {
                session.invalidate();
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin session is no longer valid.");
            }
            request.setAttribute("activeAdminChecked", id);
        }
        if (isAllowed(route, customer, driver, admin)) {
            return true;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This operation is unavailable to your account role.");
    }

    private static boolean isAllowed(String route, boolean customer, boolean driver, boolean admin) {
        if (CUSTOMER.contains(route)) return customer;
        if (DRIVER.contains(route)) return driver;
        if (CUSTOMER_OR_ADMIN.contains(route)) return customer || admin;
        if (DRIVER_OR_ADMIN.contains(route)) return driver || admin;
        if ("PATCH /api/notifications/{id}/read".equals(route)) return true;
        if ("POST /api/mobile/push-token".equals(route) || "DELETE /api/mobile/push-token".equals(route)) {
            return customer || driver;
        }
        return admin;
    }
}
