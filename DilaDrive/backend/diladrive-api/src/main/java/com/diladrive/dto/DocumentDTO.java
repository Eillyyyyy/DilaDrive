package com.diladrive.dto;

import com.diladrive.entity.Document;
import com.diladrive.entity.DocumentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

public record DocumentDTO(
        Long documentId,
        String documentNumber,
        DocumentType documentType,
        Long bookingId,
        Long sourceDocumentId,
        Long paymentId,
        Long custId,
        String custName,
        String custEmail,
        String custPhone,
        BigDecimal amount,
        String status,
        String remarks,
        LocalDateTime createdAt) {

    public static DocumentDTO from(Document document) {
        return new DocumentDTO(
                document.getDocumentId(),
                document.getDocumentNumber(),
                document.getDocumentType(),
                document.getBookingId(),
                document.getSourceDocumentId(),
                document.getPaymentId(),
                document.getCustId(),
                document.getCustName(),
                document.getCustEmail(),
                document.getCustPhone(),
                document.getAmount(),
                document.getStatus() == null
                        ? null
                        : document.getStatus().toUpperCase(Locale.ROOT),
                document.getRemarks(),
                document.getCreatedAt());
    }
}
