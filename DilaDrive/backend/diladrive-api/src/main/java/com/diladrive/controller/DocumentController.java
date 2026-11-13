package com.diladrive.controller;

import com.diladrive.dto.DocumentDashboardDTO;
import com.diladrive.dto.DocumentDTO;
import com.diladrive.dto.InvoiceSourceRequest;
import com.diladrive.dto.QuotationRequest;
import com.diladrive.entity.DocumentType;
import com.diladrive.service.DocumentPdfService;
import com.diladrive.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentPdfService documentPdfService;

    public DocumentController(
            DocumentService documentService,
            DocumentPdfService documentPdfService) {
        this.documentService = documentService;
        this.documentPdfService = documentPdfService;
    }

    @GetMapping
    public List<DocumentDTO> getAllDocuments(HttpServletRequest request) {
        requireAdminId(request);
        return documentService.getAllDocuments();
    }

    @GetMapping("/customer/mine")
    public List<DocumentDTO> getMyDocuments(HttpServletRequest request) {
        return documentService.getCustomerDocuments(requireCustomerId(request));
    }

    @GetMapping("/{id}")
    public DocumentDTO getDocument(@PathVariable Long id, HttpServletRequest request) {
        requireAdminId(request);
        return documentService.getDocument(id);
    }

    @GetMapping("/search")
    public List<DocumentDTO> searchDocuments(
            @RequestParam String query, HttpServletRequest request) {
        requireAdminId(request);
        return documentService.searchDocuments(query);
    }

    @GetMapping("/type/{type}")
    public List<DocumentDTO> getDocumentsByType(
            @PathVariable DocumentType type, HttpServletRequest request) {
        requireAdminId(request);
        return documentService.getDocumentsByType(type);
    }

    @GetMapping("/dashboard")
    public DocumentDashboardDTO getDashboard(HttpServletRequest request) {
        requireAdminId(request);
        return documentService.getDashboard();
    }

    @PostMapping("/quotation")
    public ResponseEntity<DocumentDTO> createQuotation(
            @Valid @RequestBody QuotationRequest request,
            HttpServletRequest servletRequest) {
        requireAdminId(servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.createQuotation(request));
    }

    @PutMapping("/{id}")
    public DocumentDTO updateQuotation(
            @PathVariable Long id,
            @Valid @RequestBody QuotationRequest request,
            HttpServletRequest servletRequest) {
        requireAdminId(servletRequest);
        return documentService.updateQuotation(id, request);
    }

    @PutMapping("/{id}/approve")
    public DocumentDTO approveQuotation(
            @PathVariable Long id, HttpServletRequest request) {
        requireAdminId(request);
        return documentService.approveQuotation(id);
    }

    @PutMapping("/{id}/reject")
    public DocumentDTO rejectQuotation(
            @PathVariable Long id, HttpServletRequest request) {
        requireAdminId(request);
        return documentService.rejectQuotation(id);
    }

    @PostMapping("/{id}/generate-po")
    public ResponseEntity<DocumentDTO> generatePurchaseOrder(
            @PathVariable Long id, HttpServletRequest request) {
        requireAdminId(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.generatePurchaseOrder(id));
    }

    @PostMapping("/{id}/generate-invoice")
    public ResponseEntity<DocumentDTO> generateInvoice(
            @PathVariable Long id,
            @Valid @RequestBody InvoiceSourceRequest request,
            HttpServletRequest servletRequest) {
        requireAdminId(servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.generateInvoice(id, request.sourceType()));
    }

    @PutMapping("/{id}/mark-paid")
    public DocumentDTO markInvoicePaid(
            @PathVariable Long id, HttpServletRequest request) {
        requireAdminId(request);
        return documentService.markInvoicePaid(id);
    }

    @PostMapping("/{id}/generate-receipt")
    public ResponseEntity<DocumentDTO> generateReceipt(
            @PathVariable Long id, HttpServletRequest request) {
        requireAdminId(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.generateReceipt(id));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getPdf(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean download,
            HttpServletRequest request) {
        requireAdminId(request);
        DocumentDTO document = documentService.getDocument(id);
        byte[] pdf;
        try {
            pdf = documentPdfService.generate(document);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "The document PDF could not be generated.",
                    exception);
        }

        ContentDisposition disposition = (download
                ? ContentDisposition.attachment()
                : ContentDisposition.inline())
                .filename(document.documentNumber() + ".pdf", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }

    @GetMapping("/customer/{id}/pdf")
    public ResponseEntity<byte[]> getCustomerPdf(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long customerId = requireCustomerId(request);
        DocumentDTO document = documentService.getCustomerDocument(customerId, id);
        byte[] pdf;
        try {
            pdf = documentPdfService.generate(document);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "The document PDF could not be generated.",
                    exception);
        }
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(document.documentNumber() + ".pdf", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }

    private static Long requireAdminId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object adminId = session == null ? null : session.getAttribute("adminId");
        if (adminId instanceof Long id) return id;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Please sign in as an admin.");
    }

    private static Long requireCustomerId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object role = session == null ? null : session.getAttribute("mobileRole");
        Object userId = session == null ? null : session.getAttribute("mobileUserId");
        if (!(userId instanceof Long id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Please sign in again.");
        }
        if (!"CUSTOMER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Business documents are only available to customers.");
        }
        return id;
    }
}
