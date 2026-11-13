package com.diladrive.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MobileRegistrationRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 160) String email,
        @Size(max = 30) String phone,
        @NotBlank @Pattern(regexp = "(?i)NORMAL|COMPANY") String type,
        @Size(max = 150) String companyName,
        @Size(max = 30) String companyRegistrationNumber,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
