package com.diladrive.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record MobileRegistrationRequest(
        @Size(max = 120) @Pattern(regexp = TEXT) String name,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Pattern(regexp = MALAYSIAN_PHONE, message = "must be a Malaysian telephone number") String phone,
        @NotBlank @Pattern(regexp = "NORMAL|COMPANY") String type,
        @Size(max = 150) @Pattern(regexp = TEXT) String companyName,
        @Size(max = 30) @Pattern(regexp = TEXT) String companyRegistrationNumber,
        @Size(max = 500) @Pattern(regexp = TEXT) String companyAddress,
        @Size(max = 100) @Pattern(regexp = TEXT) String contactPerson,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "(?s)(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9]).*",
                message = "must include an uppercase letter, a lowercase letter and a digit") String password,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank @Size(max = 72) String confirmPassword) {

    private static final String TEXT = "[^\\p{Cc}]*";
    public static final String MALAYSIAN_PHONE = "(?:\\+60|0)(?:11[0-9]{8}|1[02-9][0-9]{7}|3[0-9]{8}|[4-7][0-9]{7}|[89][0-9]{7,8})";

    public MobileRegistrationRequest {
        if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
        if (phone != null) phone = phone.strip();
        if (type != null) type = type.strip().toUpperCase(Locale.ROOT);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "passwords must match")
    public boolean isPasswordConfirmationValid() {
        return password == null || confirmPassword == null || password.equals(confirmPassword);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "password must not exceed 72 UTF-8 bytes")
    public boolean isPasswordByteLengthValid() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "provide the required individual or corporate account details")
    public boolean isAccountDetailsValid() {
        if ("NORMAL".equals(type)) return present(name);
        if ("COMPANY".equals(type)) {
            return present(companyName) && present(companyAddress)
                    && present(contactPerson);
        }
        return true;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "MobileRegistrationRequest";
    }
}
