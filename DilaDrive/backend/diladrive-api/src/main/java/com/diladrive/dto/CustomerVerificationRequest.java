package com.diladrive.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerVerificationRequest(
        @NotBlank @Pattern(regexp = "APPROVE|REJECT") String decision,
        @Size(max = 500) String reason) {

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unexpected verification field: " + field);
    }
}
