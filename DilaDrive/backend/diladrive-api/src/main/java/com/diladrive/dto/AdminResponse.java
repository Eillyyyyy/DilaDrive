package com.diladrive.dto;

import com.diladrive.entity.Admin;
import java.time.Instant;

public record AdminResponse(
        Long id,
        String name,
        String email,
        String phone,
        String address,
        String profileImage,
        String role,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminResponse from(Admin admin) {
        return new AdminResponse(
                admin.getId(),
                admin.getName(),
                admin.getEmail(),
                admin.getPhone(),
                admin.getAddress(),
                admin.getProfileImage(),
                admin.getRole() == null ? "ADMIN" : admin.getRole(),
                admin.getStatus() == null ? "ACTIVE" : admin.getStatus(),
                admin.getCreatedAt(),
                admin.getUpdatedAt());
    }
}
