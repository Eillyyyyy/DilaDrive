package com.diladrive.service;

import com.diladrive.dto.AdminCreateRequest;
import com.diladrive.dto.AdminResponse;
import com.diladrive.dto.AdminUpdateRequest;
import com.diladrive.entity.Admin;
import com.diladrive.repository.AdminRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminManagementService {

    private static final Set<String> ROLES = Set.of("ADMIN", "SUPER_ADMIN");
    private static final int MAX_PROFILE_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final String INVALID_IMAGE_MESSAGE =
            "The profile image must be PNG, JPG, or WebP up to 2 MB.";

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminManagementService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    public List<AdminResponse> getAllAdmins(Long actorId) {
        requireActor(actorId);
        return adminRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        Admin::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(AdminResponse::from)
                .toList();
    }

    public AdminResponse getAdminById(Long actorId, Long targetId) {
        requireActor(actorId);
        return AdminResponse.from(findAdmin(targetId));
    }

    public AdminResponse createAdmin(Long actorId, AdminCreateRequest request) {
        requireSuperAdmin(actorId);
        String name = requiredTrimmed(request.name(), "Full name");
        String email = normalizeEmail(request.email());
        String phone = requiredTrimmed(request.phone(), "Phone number");
        String address = requiredTrimmed(request.address(), "Address");
        validateLengths(name, phone, address);
        validateEmail(email);
        validatePassword(request.password());
        if (!request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        }
        if (adminRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email address is already in use.");
        }

        String role = request.role() == null || request.role().isBlank()
                ? "ADMIN"
                : request.role().trim().toUpperCase(Locale.ROOT);
        validateRole(role);

        Admin admin = new Admin();
        admin.setName(name);
        admin.setEmail(email);
        admin.setPhone(phone);
        admin.setAddress(address);
        admin.setPassword(passwordEncoder.encode(request.password()));
        admin.setProfileImage(validateProfileImage(request.profileImage()));
        admin.setRole(role);
        admin.setStatus("ACTIVE");
        return AdminResponse.from(adminRepository.save(admin));
    }

    public AdminResponse updateAdmin(
            Long actorId,
            Long targetId,
            AdminUpdateRequest request) {
        Admin actor = requireActor(actorId);
        Admin target = findAdmin(targetId);
        boolean superAdmin = "SUPER_ADMIN".equals(actor.getRole());
        if (!superAdmin && !actor.getId().equals(target.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Admins can only edit their own profile.");
        }

        String name = requiredTrimmed(request.name(), "Full name");
        String email = normalizeEmail(request.email());
        String phone = requiredTrimmed(request.phone(), "Phone number");
        String address = requiredTrimmed(request.address(), "Address");
        validateLengths(name, phone, address);
        validateEmail(email);
        if (adminRepository.existsByEmailIgnoreCaseAndIdNot(email, targetId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email address is already in use.");
        }

        String requestedRole = request.role();
        if (requestedRole != null && !requestedRole.isBlank()) {
            requestedRole = requestedRole.trim().toUpperCase(Locale.ROOT);
            validateRole(requestedRole);
            if (!superAdmin && !requestedRole.equals(target.getRole())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Only a SUPER_ADMIN can change administrator roles.");
            }
            if (superAdmin) {
                target.setRole(requestedRole);
            }
        }

        target.setName(name);
        target.setEmail(email);
        target.setPhone(phone);
        target.setAddress(address);
        target.setProfileImage(validateProfileImage(request.profileImage()));
        return AdminResponse.from(adminRepository.save(target));
    }

    private Admin requireActor(Long actorId) {
        Admin actor = findAdmin(actorId);
        if (!"ACTIVE".equalsIgnoreCase(actor.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin session is no longer valid.");
        }
        return actor;
    }

    private Admin requireSuperAdmin(Long actorId) {
        Admin actor = requireActor(actorId);
        if (!"SUPER_ADMIN".equals(actor.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only a SUPER_ADMIN can create administrator accounts.");
        }
        return actor;
    }

    private Admin findAdmin(Long id) {
        return adminRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Admin session is no longer valid."));
    }

    private static String requiredTrimmed(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required.");
        }
        return value.trim();
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateEmail(String email) {
        if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        }
    }

    private static void validateLengths(String name, String phone, String address) {
        if (name.length() > 120 || phone.length() > 30 || address.length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "One or more administrator fields are too long.");
        }
    }

    private static void validatePassword(String password) {
        if (password == null
                || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The password must be at least 8 characters and no more than 72 UTF-8 bytes.");
        }
    }

    private static void validateRole(String role) {
        if (!ROLES.contains(role)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Role must be ADMIN or SUPER_ADMIN.");
        }
    }

    private static String validateProfileImage(String image) {
        if (image == null || image.isBlank()) {
            return null;
        }
        if (!image.matches("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/]+={0,2}$")
                || image.length() > 2_800_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_IMAGE_MESSAGE);
        }
        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The profile image is not valid base64 data.");
        }
        if (imageBytes.length > MAX_PROFILE_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_IMAGE_MESSAGE);
        }
        return image;
    }
}
