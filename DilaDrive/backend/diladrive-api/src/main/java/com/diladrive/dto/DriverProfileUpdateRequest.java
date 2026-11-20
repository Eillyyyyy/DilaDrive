package com.diladrive.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DriverProfileUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(max = 30) String phone,
        @Size(max = 1300000) String profileImage) {
    public DriverProfileUpdateRequest(String name, String email, String phone) {
        this(name, email, phone, null);
    }
    @Override public String toString() { return "DriverProfileUpdateRequest[profileImage=REDACTED]"; }
}
