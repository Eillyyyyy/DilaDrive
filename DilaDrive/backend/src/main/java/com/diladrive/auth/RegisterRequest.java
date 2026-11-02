package com.diladrive.auth;

import com.diladrive.user.CustomerType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Size(max = 20) String phoneNo,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
                message = "Username must be 3-50 characters: letters, digits, dot, underscore or dash") String username,
        @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password,
        @NotNull CustomerType customerType,
        @Size(max = 150) String companyName,
        @Size(max = 50) String supplierNo,
        @Size(max = 100) String contactPerson,
        String billingAddress) {
}
