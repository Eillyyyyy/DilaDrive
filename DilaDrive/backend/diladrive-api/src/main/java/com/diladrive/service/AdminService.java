package com.diladrive.service;

import com.diladrive.entity.Admin;
import com.diladrive.dto.AdminProfileResponse;
import com.diladrive.dto.ChangePasswordRequest;
import com.diladrive.dto.ProfileUpdateRequest;
import com.diladrive.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

@Service
public class AdminService {

    @Autowired
    private AdminRepository adminRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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

        if (isPasswordHash(admin.getPassword())) {
            return passwordEncoder.matches(password, admin.getPassword());
        }
        if (!Objects.equals(admin.getPassword(), password)) {
            return false;
        }

        admin.setPassword(passwordEncoder.encode(password));
        adminRepository.save(admin);
        return true;
    }

    public AdminProfileResponse getProfile(String email) {
        return new AdminProfileResponse(findAdmin(email));
    }

    public AdminProfileResponse updateProfile(
            String email,
            ProfileUpdateRequest request) {
        Admin admin = findAdmin(email);
        verifyCurrentPassword(admin, request.getCurrentPassword());

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
        Admin admin = findAdmin(email);
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

        admin.setPassword(passwordEncoder.encode(newPassword));
        adminRepository.save(admin);
    }

    private Admin findAdmin(String email) {
        return adminRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("Admin profile was not found."));
    }

    private void verifyCurrentPassword(Admin admin, String currentPassword) {
        if (!passwordMatches(admin.getPassword(), currentPassword)) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (!isPasswordHash(admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode(currentPassword));
        }
    }

    private boolean passwordMatches(String storedPassword, String password) {
        if (password == null) {
            return false;
        }
        return isPasswordHash(storedPassword)
                ? passwordEncoder.matches(password, storedPassword)
                : Objects.equals(storedPassword, password);
    }

    private boolean isPasswordHash(String value) {
        return value != null
                && value.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    }
}