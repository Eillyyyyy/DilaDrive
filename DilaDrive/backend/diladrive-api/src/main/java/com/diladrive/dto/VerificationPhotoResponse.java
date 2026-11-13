package com.diladrive.dto;

import com.diladrive.entity.VerificationPhoto;
import java.time.LocalDateTime;

public record VerificationPhotoResponse(Long photoId, Long assignmentId, String photoType,
        String fileUrl, LocalDateTime uploadedAt) {
    public static VerificationPhotoResponse from(VerificationPhoto photo) {
        return new VerificationPhotoResponse(photo.getPhotoId(),
                photo.getAssignment().getAssignmentId(), photo.getPhotoType(),
                photo.getFileUrl(), photo.getUploadedAt());
    }
}
