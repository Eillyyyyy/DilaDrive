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
import java.util.Locale;
import java.util.Map;

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

    @GetMapping("/inbox")
    public NotificationService.InboxPage inbox(HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "") String search) {
        Long adminId = requireAdmin(request);
        validatePage(page, size);
        Boolean read = switch (status.toUpperCase(Locale.ROOT)) {
            case "ALL" -> null;
            case "READ" -> true;
            case "UNREAD" -> false;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown notification status.");
        };
        if (type.length() > 50 || search.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notification filter is too long.");
        }
        return notificationService.inbox(adminId, page, size, read,
                type.trim().toUpperCase(Locale.ROOT), search.trim());
    }

    @GetMapping("/sent/driver")
    public NotificationService.InboxPage sentDriverNotifications(HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        requireAdmin(request);
        validatePage(page, size);
        return notificationService.sentDriverNotifications(page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(HttpServletRequest request) {
        return Map.of("count", notificationService.unreadCount(requireAdmin(request)));
    }

    @PatchMapping("/read-all")
    public Map<String, Long> markAllRead(HttpServletRequest request) {
        return Map.of("count", notificationService.markAllRead(requireAdmin(request)));
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notification page.");
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse createNotification(
            @Valid @RequestBody NotificationCreateRequest request,
            HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return notificationService.createNotification(request);
    }

    @MobilePagination.Paged
    @GetMapping("/driver/{driverId}")
    public List<NotificationResponse> getDriverNotifications(
            @PathVariable Long driverId,
            HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null && session.getAttribute("adminId") instanceof Long) {
            return notificationService.getDriverNotifications(driverId);
        }
        if (!"DRIVER".equals(mobileRole(request))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Driver sign-in is required.");
        }
        Long sessionDriver = mobileUserId(request);
        if (!sessionDriver.equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "These notifications are not available to this account.");
        }
        return notificationService.getDriverNotifications(sessionDriver);
    }

    @MobilePagination.Paged
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

    private static Long requireAdmin(HttpServletRequest request) {
        var session = request.getSession(false);
        Object adminId = session == null ? null : session.getAttribute("adminId");
        if (!(adminId instanceof Long id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Please sign in as an admin.");
        }
        return id;
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
