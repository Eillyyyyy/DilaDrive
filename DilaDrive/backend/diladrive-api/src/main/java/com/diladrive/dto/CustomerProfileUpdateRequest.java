package com.diladrive.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerProfileUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 160) String email,
        @Size(max = 30) String phone,
        @Size(max = 150) String companyName,
        @Size(max = 30) String companyRegistrationNumber,
        @Size(max = 1400000) String profileImage) {
}
