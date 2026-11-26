package com.diladrive.dto;

import java.time.Instant;

public record CustomerVerificationResponse(
        Long customerId, String category, String status, String decision,
        Instant verifiedAt, Long reviewerId, String rejectionReason) {
}
