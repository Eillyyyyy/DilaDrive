package com.diladrive.controller;

import com.diladrive.dto.LoginRequest;
import com.diladrive.dto.AdminProfileResponse;
import com.diladrive.dto.ChangePasswordRequest;
import com.diladrive.dto.MobileLoginRequest;
import com.diladrive.dto.MobileLoginResponse;
import com.diladrive.dto.ProfileUpdateRequest;
import com.diladrive.service.AdminService;
import com.diladrive.service.MobileAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/auth")
public class AuthController {


    @Autowired
    private AdminService adminService;

    private final MobileAuthService mobileAuthService;

    public AuthController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (!adminService.login(request.getEmail(), request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid Email or Password");
        }

        return ResponseEntity.ok(adminService.getProfile(request.getEmail()));
    }

    @PostMapping("/mobile/login")
    public ResponseEntity<MobileLoginResponse> mobileLogin(
            @RequestBody MobileLoginRequest request) {
        return mobileAuthService.login(request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestParam String email,
            @RequestBody ProfileUpdateRequest request) {
        try {
            AdminProfileResponse profile = adminService.updateProfile(email, request);
            return ResponseEntity.ok(profile);
        } catch (IllegalArgumentException exception) {
            HttpStatus status = "Current password is incorrect.".equals(exception.getMessage())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(exception.getMessage());
        }
    }

    @PostMapping("/profile/password")
    public ResponseEntity<?> changePassword(
            @RequestParam String email,
            @RequestBody ChangePasswordRequest request) {
        try {
            adminService.changePassword(email, request);
            return ResponseEntity.ok("Password changed successfully.");
        } catch (IllegalArgumentException exception) {
            HttpStatus status = "Current password is incorrect.".equals(exception.getMessage())
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(exception.getMessage());
        }
    }
}