package com.diladrive.controller;

import com.diladrive.security.AdminSession;
import com.diladrive.security.LoginThrottle;

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



@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String INVALID_LOGIN = "Invalid Email or Password";
    private final LoginThrottle loginThrottle = new LoginThrottle();

    @Autowired
    private AdminService adminService;

    private final MobileAuthService mobileAuthService;

    public AuthController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        Runnable success = loginThrottle.begin(request.getEmail(), servletRequest.getRemoteAddr());
        if (success == null) return throttled();
        if (!adminService.login(request.getEmail(), request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID_LOGIN);
        }
        success.run();
        HttpSession existingSession = servletRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        AdminProfileResponse profile = adminService.getProfile(request.getEmail());
        servletRequest.getSession(true).setAttribute("adminId", profile.getId());
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/session")
    public ResponseEntity<AdminProfileResponse> getSession(HttpServletRequest request) {
        return ResponseEntity.ok(adminService.getProfile(requireAdminId(request)));
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
    public ResponseEntity<?> mobileLogin(
            @Valid @RequestBody MobileLoginRequest request,
            HttpServletRequest servletRequest) {
        Runnable success = loginThrottle.begin(request.getEmail(), servletRequest.getRemoteAddr());
        if (success == null) return throttled();
        var result = mobileAuthService.login(request);
        if (result.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(INVALID_LOGIN);
        }

        success.run();
        HttpSession existingSession = servletRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        MobileLoginResponse user = result.get();
        HttpSession session = servletRequest.getSession(true);
        session.setAttribute("mobileUserId", user.getId());
        session.setAttribute("mobileRole", user.getRole());
        return ResponseEntity.ok(user);
    }

    private static ResponseEntity<String> throttled() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "900")
                .body("Too many sign-in attempts. Try again later.");
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
            @Valid @RequestBody ProfileUpdateRequest request) {
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
            @Valid @RequestBody ChangePasswordRequest request) {
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
        return AdminSession.requireAdminId(request);
    }
}
