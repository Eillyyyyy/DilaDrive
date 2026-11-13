package com.diladrive.service;

import com.diladrive.dto.DocumentDashboardDTO;
import com.diladrive.dto.DocumentDTO;
import com.diladrive.dto.QuotationRequest;
import com.diladrive.entity.DocumentType;
import com.diladrive.entity.InvoiceSourceType;
import com.diladrive.entity.Payment;

import java.util.List;

public interface DocumentService {
    List<DocumentDTO> getAllDocuments();

    List<DocumentDTO> getCustomerDocuments(Long customerId);

    DocumentDTO getCustomerDocument(Long customerId, Long documentId);

    DocumentDTO getDocument(Long id);

    List<DocumentDTO> searchDocuments(String query);

    List<DocumentDTO> getDocumentsByType(DocumentType type);

    DocumentDashboardDTO getDashboard();

    DocumentDTO createQuotation(QuotationRequest request);

    DocumentDTO updateQuotation(Long id, QuotationRequest request);

    DocumentDTO approveQuotation(Long id);

    DocumentDTO rejectQuotation(Long id);

    DocumentDTO generatePurchaseOrder(Long quotationId);

    DocumentDTO generateInvoice(Long sourceId, InvoiceSourceType sourceType);

    DocumentDTO markInvoicePaid(Long invoiceId);

    DocumentDTO generateReceipt(Long invoiceId);

    DocumentDTO generateReceiptForPayment(Payment payment);
}
