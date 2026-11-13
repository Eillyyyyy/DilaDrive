package com.diladrive.service;

import com.diladrive.entity.Admin;
import com.diladrive.dto.AdminProfileResponse;
import com.diladrive.dto.ChangePasswordRequest;
import com.diladrive.dto.ProfileUpdateRequest;
import com.diladrive.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordService passwordService;

    public boolean login(String email, String password) {
        if (email == null || password == null) {
            return false;
        }

        Admin admin = adminRepository
                .findByEmailIgnoreCase(email.trim())
                .orElse(null);

        if (admin == null) {
            return false;
        }
        if (!"ACTIVE".equalsIgnoreCase(admin.getStatus())) {
            return false;
        }

        if (!passwordService.matches(password, admin.getPassword())) {
            return false;
        }

        if (!passwordService.isEncoded(admin.getPassword())) {
            admin.setPassword(passwordService.encode(password));
            adminRepository.save(admin);
        }
        return true;
    }

    public AdminProfileResponse getProfile(String email) {
        return new AdminProfileResponse(findAdmin(email));
    }

    public AdminProfileResponse getProfile(Long adminId) {
        return new AdminProfileResponse(findAdmin(adminId));
    }

    public AdminProfileResponse updateProfile(
            String email,
            ProfileUpdateRequest request) {
        return updateProfile(findAdmin(email).getId(), request);
    }

    public AdminProfileResponse updateProfile(
            Long adminId,
            ProfileUpdateRequest request) {
        Admin admin = findAdmin(adminId);

        String name = request.getName() == null ? "" : request.getName().trim();
        String updatedEmail = request.getEmail() == null
                ? ""
                : request.getEmail().trim().toLowerCase();
        String phone = request.getPhone() == null ? "" : request.getPhone().trim();
        String address = request.getAddress() == null
                ? ""
                : request.getAddress().trim();

        if (name.isBlank() || updatedEmail.isBlank()) {
            throw new IllegalArgumentException("Name and email are required.");
        }
        if (updatedEmail.length() > 254 || !updatedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (name.length() > 120 || phone.length() > 30 || address.length() > 500) {
            throw new IllegalArgumentException("One or more profile fields are too long.");
        }
        if (adminRepository.existsByEmailIgnoreCaseAndIdNot(updatedEmail, admin.getId())) {
            throw new IllegalArgumentException("That email address is already in use.");
        }

        String profileImage = request.getProfileImage();
        if (profileImage != null && !profileImage.isBlank()) {
            if (!profileImage.matches("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/]+={0,2}$")
                    || profileImage.length() > 2_800_000) {
                throw new IllegalArgumentException("The profile image must be PNG, JPG, or WebP up to 2 MB.");
            }
            byte[] imageBytes;
            try {
                imageBytes = Base64.getDecoder().decode(
                        profileImage.substring(profileImage.indexOf(',') + 1));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("The profile image is not valid base64 data.");
            }
            if (imageBytes.length > 2 * 1024 * 1024) {
                throw new IllegalArgumentException("The profile image must be PNG, JPG, or WebP up to 2 MB.");
            }
        } else {
            profileImage = null;
        }

        admin.setName(name);
        admin.setEmail(updatedEmail);
        admin.setPhone(phone);
        admin.setAddress(address);
        admin.setProfileImage(profileImage);
        return new AdminProfileResponse(adminRepository.save(admin));
    }

    public void changePassword(String email, ChangePasswordRequest request) {
        changePassword(findAdmin(email).getId(), request);
    }

    public void changePassword(Long adminId, ChangePasswordRequest request) {
        Admin admin = findAdmin(adminId);
        verifyCurrentPassword(admin, request.getCurrentPassword());

        String newPassword = request.getNewPassword();
        if (newPassword == null
                || newPassword.length() < 8
                || newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("The new password must be at least 8 characters and no more than 72 UTF-8 bytes.");
        }
        if (Objects.equals(newPassword, request.getCurrentPassword())) {
            throw new IllegalArgumentException("The new password must be different from the current password.");
        }

        admin.setPassword(passwordService.encode(newPassword));
        adminRepository.save(admin);
    }

    private Admin findAdmin(String email) {
        return adminRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("Admin profile was not found."));
    }

    private Admin findAdmin(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Admin session is no longer valid."));
        if (!"ACTIVE".equalsIgnoreCase(admin.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Admin session is no longer valid.");
        }
        return admin;
    }

    private void verifyCurrentPassword(Admin admin, String currentPassword) {
        if (!passwordService.matches(currentPassword, admin.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (!passwordService.isEncoded(admin.getPassword())) {
            admin.setPassword(passwordService.encode(currentPassword));
        }
    }
}