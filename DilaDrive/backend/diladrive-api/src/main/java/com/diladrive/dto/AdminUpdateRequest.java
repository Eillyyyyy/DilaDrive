package com.diladrive.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 30) String phone,
        @NotBlank @Size(max = 500) String address,
        String profileImage,
        @Size(max = 20) String role) {
}
