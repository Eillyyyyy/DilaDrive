package com.diladrive.controller;

import com.diladrive.dto.LoginRequest;
import com.diladrive.dto.AdminProfileResponse;
import com.diladrive.dto.ChangePasswordRequest;
import com.diladrive.dto.MobileLoginRequest;
import com.diladrive.dto.MobileLoginResponse;
import com.diladrive.dto.MobileRegistrationRequest;
import com.diladrive.dto.ProfileUpdateRequest;
import com.diladrive.service.AdminService;
import com.diladrive.service.MobileAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AdminService adminService;

    private final MobileAuthService mobileAuthService;

    public AuthController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        logger.info("Admin login request received from source={}", servletRequest.getRemoteAddr());
        if (!adminService.login(request.getEmail(), request.getPassword())) {
            logger.warn("Admin login failed from source={}", servletRequest.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid Email or Password");
        }

        HttpSession existingSession = servletRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        AdminProfileResponse profile = adminService.getProfile(request.getEmail());
        servletRequest.getSession(true).setAttribute("adminId", profile.getId());
        logger.info("Admin login succeeded from source={}", servletRequest.getRemoteAddr());
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/session")
    public ResponseEntity<AdminProfileResponse> getSession(HttpServletRequest request) {
        var session = request.getSession(false);
        Object adminId = session == null ? null : session.getAttribute("adminId");
        if (!(adminId instanceof Long id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(adminService.getProfile(id));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mobile/login")
    public ResponseEntity<MobileLoginResponse> mobileLogin(
            @RequestBody MobileLoginRequest request,
            HttpServletRequest servletRequest) {
        String source = servletRequest.getRemoteAddr();
        String role = request.getRole();
        logger.info("Mobile login request received from source={}, role={}", source, role);
        var result = mobileAuthService.login(request);
        if (result.isPresent()) {
            HttpSession existingSession = servletRequest.getSession(false);
            if (existingSession != null) {
                existingSession.invalidate();
            }
            MobileLoginResponse user = result.get();
            HttpSession session = servletRequest.getSession(true);
            session.setAttribute("mobileUserId", user.getId());
            session.setAttribute("mobileRole", user.getRole());
            logger.info("Mobile login succeeded from source={}, role={}", source, role);
            return ResponseEntity.ok(user);
        }
        logger.warn("Mobile login failed from source={}, role={}", source, role);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping("/mobile/register")
    public ResponseEntity<String> mobileRegister(
            @Valid @RequestBody MobileRegistrationRequest request) {
        try {
            mobileAuthService.registerCustomer(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("Your customer account was created and is awaiting Admin verification.");
        } catch (org.springframework.web.server.ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode())
                    .body(exception.getReason());
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            HttpServletRequest servletRequest,
            @RequestBody ProfileUpdateRequest request) {
        try {
            AdminProfileResponse profile = adminService.updateProfile(requireAdminId(servletRequest), request);
            return ResponseEntity.ok(profile);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @PostMapping("/profile/password")
    public ResponseEntity<?> changePassword(
            HttpServletRequest servletRequest,
            @RequestBody ChangePasswordRequest request) {
        try {
            adminService.changePassword(requireAdminId(servletRequest), request);
            return ResponseEntity.ok("Password changed successfully.");
        } catch (IllegalArgumentException exception) {
            HttpStatus status = "Current password is incorrect.".equals(exception.getMessage())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(exception.getMessage());
        }
    }

    private static Long requireAdminId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object adminId = session == null ? null : session.getAttribute("adminId");
        if (adminId instanceof Long id) {
            return id;
        }
        throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Please sign in again.");
    }
}
