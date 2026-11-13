package com.diladrive.controller;

import com.diladrive.dto.NotificationResponse;
import com.diladrive.dto.NotificationCreateRequest;
import com.diladrive.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> getAllNotifications(HttpServletRequest request) {
        return notificationService.getAdminNotifications(requireAdmin(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse createNotification(
            @Valid @RequestBody NotificationCreateRequest request,
            HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return notificationService.createNotification(request);
    }

    @GetMapping("/driver/{driverId}")
    public List<NotificationResponse> getDriverNotifications(
            @PathVariable Long driverId,
            HttpServletRequest request) {
        requireMobileOwnerOrAdmin(request, "DRIVER", driverId);
        return notificationService.getDriverNotifications(driverId);
    }

    @GetMapping("/customer/mine")
    public List<NotificationResponse> getMyCustomerNotifications(
            HttpServletRequest request) {
        return notificationService.getCustomerNotifications(requireCustomerId(request));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(
            @PathVariable Long id,
            HttpServletRequest request) {
        var session = request.getSession(false);
        Object adminId = session == null ? null : session.getAttribute("adminId");
        if (adminId instanceof Long) {
            return notificationService.markAdminNotificationAsRead(id, (Long) adminId);
        }
        String role = mobileRole(request);
        Long recipientId = mobileUserId(request);
        return notificationService.markAsReadByOwner(id, role, recipientId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNotification(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        notificationService.deleteNotification(id);
    }

    private static Long requireAdmin(HttpServletRequest request) {
        var session = request.getSession(false);
        Object adminId = session == null ? null : session.getAttribute("adminId");
        if (!(adminId instanceof Long id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Please sign in as an admin.");
        }
        return id;
    }

    private static void requireMobileOwnerOrAdmin(
            HttpServletRequest request, String expectedRole, Long expectedId) {
        var session = request.getSession(false);
        if (session != null && session.getAttribute("adminId") instanceof Long) {
            return;
        }
        String role = mobileRole(request);
        Long id = mobileUserId(request);
        if (!expectedRole.equals(role) || !expectedId.equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "These notifications are not available to this account.");
        }
    }

    private static String mobileRole(HttpServletRequest request) {
        var session = request.getSession(false);
        Object role = session == null ? null : session.getAttribute("mobileRole");
        if (!(role instanceof String value)
                || (!"CUSTOMER".equals(value) && !"DRIVER".equals(value))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Please sign in again.");
        }
        return value;
    }

    private static Long mobileUserId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute("mobileUserId");
        if (!(userId instanceof Long id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Please sign in again.");
        }
        return id;
    }

    private static Long requireCustomerId(HttpServletRequest request) {
        if (!"CUSTOMER".equals(mobileRole(request))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "These notifications are only available to customer accounts.");
        }
        return mobileUserId(request);
    }
}
