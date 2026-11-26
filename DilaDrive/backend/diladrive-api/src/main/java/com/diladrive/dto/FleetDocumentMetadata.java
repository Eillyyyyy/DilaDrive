package com.diladrive.dto;

import com.diladrive.entity.FleetDocument;
import java.time.LocalDateTime;

public record FleetDocumentMetadata(Long id, String type, String filename,
        String contentType, Long size, LocalDateTime uploadedAt) {
    public static FleetDocumentMetadata from(FleetDocument document) {
        if (document == null || !document.isCurrent()) return null;
        return new FleetDocumentMetadata(document.getFleetDocumentId(),
                document.getDocumentType(), document.getOriginalFilename(),
                document.getContentType(), document.getFileSize(),
                document.getUploadedAt());
    }
}
