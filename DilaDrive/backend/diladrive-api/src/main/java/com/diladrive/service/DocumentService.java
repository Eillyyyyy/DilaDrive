package com.diladrive.service;

import com.diladrive.dto.DocumentDashboardDTO;
import com.diladrive.dto.DocumentDTO;
import com.diladrive.dto.DocumentRequest;
import com.diladrive.dto.InvoiceGenerationRequest;
import com.diladrive.dto.ReceiptGenerationRequest;
import com.diladrive.entity.DocumentType;

import java.util.List;

public interface DocumentService {
    List<DocumentDTO> getAllDocuments();

    DocumentDTO getDocument(Long id);

    List<DocumentDTO> searchDocuments(String query);

    List<DocumentDTO> getDocumentsByType(DocumentType type);

    DocumentDashboardDTO getDashboard();

    DocumentDTO createQuotation(DocumentRequest request);

    DocumentDTO updateQuotation(Long id, DocumentRequest request);

    DocumentDTO approveQuotation(Long id);

    DocumentDTO rejectQuotation(Long id);

    DocumentDTO generatePurchaseOrder(Long quotationId);

    DocumentDTO generateInvoice(InvoiceGenerationRequest request);

    DocumentDTO generateReceipt(Long invoiceId, ReceiptGenerationRequest request);

    void deleteDocument(Long id);
}
