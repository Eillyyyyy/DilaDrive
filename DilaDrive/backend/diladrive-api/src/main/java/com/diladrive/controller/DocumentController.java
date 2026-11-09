package com.diladrive.controller;

import com.diladrive.dto.DocumentDashboardDTO;
import com.diladrive.dto.DocumentDTO;
import com.diladrive.dto.DocumentRequest;
import com.diladrive.dto.InvoiceGenerationRequest;
import com.diladrive.dto.ReceiptGenerationRequest;
import com.diladrive.entity.DocumentType;
import com.diladrive.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "http://localhost:5173")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<DocumentDTO> getAllDocuments() {
        return documentService.getAllDocuments();
    }

    @GetMapping("/{id}")
    public DocumentDTO getDocument(@PathVariable Long id) {
        return documentService.getDocument(id);
    }

    @PostMapping
    public ResponseEntity<DocumentDTO> createDocument(
            @Valid @RequestBody DocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.createQuotation(request));
    }

    @PutMapping("/{id}")
    public DocumentDTO updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRequest request) {
        return documentService.updateQuotation(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public List<DocumentDTO> searchDocuments(@RequestParam String query) {
        return documentService.searchDocuments(query);
    }

    @GetMapping("/type/{type}")
    public List<DocumentDTO> getDocumentsByType(@PathVariable DocumentType type) {
        return documentService.getDocumentsByType(type);
    }

    @GetMapping("/dashboard")
    public DocumentDashboardDTO getDashboard() {
        return documentService.getDashboard();
    }

    @PostMapping("/{id}/approve")
    public DocumentDTO approveQuotation(@PathVariable Long id) {
        return documentService.approveQuotation(id);
    }

    @PostMapping("/{id}/reject")
    public DocumentDTO rejectQuotation(@PathVariable Long id) {
        return documentService.rejectQuotation(id);
    }

    @PostMapping("/{id}/purchase-order")
    public ResponseEntity<DocumentDTO> generatePurchaseOrder(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.generatePurchaseOrder(id));
    }

    @PostMapping("/invoices")
    public ResponseEntity<DocumentDTO> generateInvoice(
            @Valid @RequestBody InvoiceGenerationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.generateInvoice(request));
    }

    @PostMapping("/{id}/receipt")
    public ResponseEntity<DocumentDTO> generateReceipt(
            @PathVariable Long id,
            @Valid @RequestBody ReceiptGenerationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.generateReceipt(id, request));
    }
}
