package com.diladrive.service;

import com.diladrive.dto.DocumentDashboardDTO;
import com.diladrive.dto.DocumentDTO;
import com.diladrive.dto.DocumentRequest;
import com.diladrive.dto.InvoiceGenerationRequest;
import com.diladrive.dto.ReceiptGenerationRequest;
import com.diladrive.entity.Booking;
import com.diladrive.entity.Customer;
import com.diladrive.entity.Document;
import com.diladrive.entity.DocumentType;
import com.diladrive.repository.BookingRepository;
import com.diladrive.repository.CustomerRepository;
import com.diladrive.repository.DocumentDashboardProjection;
import com.diladrive.repository.DocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;

    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            BookingRepository bookingRepository,
            CustomerRepository customerRepository) {
        this.documentRepository = documentRepository;
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public List<DocumentDTO> getAllDocuments() {
        return documentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(DocumentDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDTO getDocument(Long id) {
        return DocumentDTO.from(findDocument(id));
    }

    @Transactional(readOnly = true)
    public List<DocumentDTO> searchDocuments(String query) {
        if (query == null || query.isBlank()) {
            return getAllDocuments();
        }
        String normalized = query.trim();
        return documentRepository
                .findByDocumentNumberContainingIgnoreCaseOrCustNameContainingIgnoreCaseOrderByCreatedAtDesc(
                        normalized,
                        normalized)
                .stream()
                .map(DocumentDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentDTO> getDocumentsByType(DocumentType type) {
        return documentRepository.findByDocumentTypeOrderByCreatedAtDesc(type)
                .stream()
                .map(DocumentDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDashboardDTO getDashboard() {
        DocumentDashboardProjection stats = documentRepository.getDashboardStatistics();
        return new DocumentDashboardDTO(
                valueOrZero(stats.getTotalQuotations()),
                valueOrZero(stats.getTotalPurchaseOrders()),
                valueOrZero(stats.getTotalInvoices()),
                valueOrZero(stats.getTotalReceipts()),
                stats.getTotalReceiptAmount() == null
                        ? java.math.BigDecimal.ZERO
                        : stats.getTotalReceiptAmount());
    }

    @Transactional
    public DocumentDTO createQuotation(DocumentRequest request) {
        if (request.documentType() != DocumentType.QUOTATION) {
            throw badRequest("Create a quotation directly; generate other document types from their source records.");
        }
        Booking booking = findBookingForUpdate(request.bookingId());
        Customer customer = findCustomer(request.custId());
        validateBookingCustomer(booking, customer.getCustId());

        Document document = new Document();
        document.setDocumentType(DocumentType.QUOTATION);
        document.setBookingId(booking.getBookingId());
        document.setStatus("DRAFT");
        applyCustomerAndRequest(document, customer, request.amount(), request.remarks());
        return saveWithGeneratedNumber(document);
    }

    @Transactional
    public DocumentDTO updateQuotation(Long id, DocumentRequest request) {
        if (request.documentType() != DocumentType.QUOTATION) {
            throw badRequest("Only quotation documents can be updated.");
        }

        Document document = findDocumentForUpdate(id);
        requireType(document, DocumentType.QUOTATION);
        if (!"DRAFT".equalsIgnoreCase(document.getStatus())
                && !"REJECTED".equalsIgnoreCase(document.getStatus())) {
            throw conflict("Only draft or rejected quotations can be edited.");
        }

        Booking booking = findBookingForUpdate(request.bookingId());
        Customer customer = findCustomer(request.custId());
        validateBookingCustomer(booking, customer.getCustId());

        document.setBookingId(booking.getBookingId());
        document.setStatus("DRAFT");
        applyCustomerAndRequest(document, customer, request.amount(), request.remarks());
        return DocumentDTO.from(documentRepository.save(document));
    }

    @Transactional
    public DocumentDTO approveQuotation(Long id) {
        Document document = findDocumentForUpdate(id);
        requireType(document, DocumentType.QUOTATION);
        requireStatus(document, "DRAFT", "Only draft quotations can be approved.");
        document.setStatus("APPROVED");
        return DocumentDTO.from(documentRepository.save(document));
    }

    @Transactional
    public DocumentDTO rejectQuotation(Long id) {
        Document document = findDocumentForUpdate(id);
        requireType(document, DocumentType.QUOTATION);
        requireStatus(document, "DRAFT", "Only draft quotations can be rejected.");
        document.setStatus("REJECTED");
        return DocumentDTO.from(documentRepository.save(document));
    }

    @Transactional
    public DocumentDTO generatePurchaseOrder(Long quotationId) {
        Document quotation = findDocumentForUpdate(quotationId);
        requireType(quotation, DocumentType.QUOTATION);
        requireStatus(quotation, "APPROVED",
                "A purchase order can only be generated from an approved quotation.");

        String sourceReference = "Quotation: " + quotation.getDocumentNumber() + ";";
        if (documentRepository.existsByDocumentTypeAndRemarksStartingWith(
                DocumentType.PURCHASE_ORDER, sourceReference)) {
            throw conflict("A purchase order has already been generated for this quotation.");
        }

        Document purchaseOrder = copyDocument(quotation, DocumentType.PURCHASE_ORDER);
        purchaseOrder.setStatus("GENERATED");
        purchaseOrder.setRemarks(appendRemarks(sourceReference, quotation.getRemarks()));
        return saveWithGeneratedNumber(purchaseOrder);
    }

    @Transactional
    public DocumentDTO generateInvoice(InvoiceGenerationRequest request) {
        Booking booking = findBookingForUpdate(request.bookingId());
        if (booking.getCustId() == null) {
            throw conflict("The booking does not have a customer.");
        }
        if (documentRepository.existsByDocumentTypeAndBookingId(
                DocumentType.INVOICE, booking.getBookingId())) {
            throw conflict("An invoice already exists for this booking.");
        }

        Customer customer = findCustomer(booking.getCustId());
        Document invoice = new Document();
        invoice.setDocumentType(DocumentType.INVOICE);
        invoice.setBookingId(booking.getBookingId());
        invoice.setStatus("ISSUED");
        applyCustomerAndRequest(invoice, customer, request.amount(), request.remarks());
        return saveWithGeneratedNumber(invoice);
    }

    @Transactional
    public DocumentDTO generateReceipt(
            Long invoiceId,
            ReceiptGenerationRequest request) {
        Document invoice = findDocumentForUpdate(invoiceId);
        requireType(invoice, DocumentType.INVOICE);
        requireStatus(invoice, "ISSUED",
                "A receipt can only be generated for an unpaid issued invoice.");
        if (!Boolean.TRUE.equals(request.paymentConfirmed())) {
            throw badRequest("Confirm payment before generating a receipt.");
        }

        Document receipt = copyDocument(invoice, DocumentType.RECEIPT);
        receipt.setStatus("PAID");
        receipt.setRemarks(
                "Invoice: " + invoice.getDocumentNumber()
                        + "; Payment reference: " + request.paymentReference().trim()
                        + "; Method: " + normalizedMethod(request.paymentMethod()));

        DocumentDTO result = saveWithGeneratedNumber(receipt);
        invoice.setStatus("PAID");
        documentRepository.save(invoice);
        return result;
    }

    @Transactional
    public void deleteDocument(Long id) {
        Document document = findDocumentForUpdate(id);
        if (document.getDocumentType() == DocumentType.INVOICE
                && documentRepository.existsByDocumentTypeAndRemarksStartingWith(
                        DocumentType.RECEIPT,
                        "Invoice: " + document.getDocumentNumber() + ";")) {
            throw conflict("An invoice with a receipt cannot be deleted.");
        }
        if (document.getDocumentType() == DocumentType.QUOTATION
                && documentRepository.existsByDocumentTypeAndRemarksStartingWith(
                        DocumentType.PURCHASE_ORDER,
                        "Quotation: " + document.getDocumentNumber() + ";")) {
            throw conflict("A quotation with a generated purchase order cannot be deleted.");
        }
        if (document.getDocumentType() == DocumentType.RECEIPT) {
            revertInvoiceStatus(document.getRemarks());
        }
        documentRepository.delete(document);
    }

    private DocumentDTO saveWithGeneratedNumber(Document document) {
        document.setDocumentNumber(
                "TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        Document saved = documentRepository.saveAndFlush(document);
        saved.setDocumentNumber(
                numberPrefix(saved.getDocumentType()) + "-"
                        + Long.toString(saved.getDocumentId(), 36).toUpperCase(Locale.ROOT));
        return DocumentDTO.from(documentRepository.save(saved));
    }

    private Document copyDocument(Document source, DocumentType type) {
        Document target = new Document();
        target.setDocumentType(type);
        target.setBookingId(source.getBookingId());
        target.setCustId(source.getCustId());
        target.setCustName(source.getCustName());
        target.setCustEmail(source.getCustEmail());
        target.setCustPhone(source.getCustPhone());
        target.setAmount(source.getAmount());
        return target;
    }

    private void applyCustomerAndRequest(
            Document document,
            Customer customer,
            java.math.BigDecimal amount,
            String remarks) {
        document.setCustId(customer.getCustId());
        document.setCustName(customer.getCustName());
        document.setCustEmail(customer.getCustEmail());
        document.setCustPhone(customer.getCustPhoneno());
        document.setAmount(amount);
        document.setRemarks(remarks == null || remarks.isBlank() ? null : remarks.trim());
    }

    private Booking findBookingForUpdate(Long bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found."));
    }

    private Customer findCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customer not found."));
    }

    private Document findDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Document not found."));
    }

    private Document findDocumentForUpdate(Long id) {
        return documentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Document not found."));
    }

    private void validateBookingCustomer(Booking booking, Long customerId) {
        if (!customerId.equals(booking.getCustId())) {
            throw badRequest("The selected customer does not own the selected booking.");
        }
    }

    private void requireType(Document document, DocumentType type) {
        if (document.getDocumentType() != type) {
            throw badRequest("This action is only valid for " + type + " documents.");
        }
    }

    private void requireStatus(Document document, String status, String message) {
        if (!status.equalsIgnoreCase(document.getStatus())) {
            throw conflict(message);
        }
    }

    private void revertInvoiceStatus(String receiptRemarks) {
        if (receiptRemarks == null || !receiptRemarks.startsWith("Invoice: ")) {
            return;
        }
        int separator = receiptRemarks.indexOf(';');
        if (separator < 0) {
            return;
        }
        String invoiceNumber = receiptRemarks.substring("Invoice: ".length(), separator);
        documentRepository.findByDocumentNumber(invoiceNumber)
                .filter(invoice -> invoice.getDocumentType() == DocumentType.INVOICE)
                .ifPresent(invoice -> {
                    invoice.setStatus("ISSUED");
                    documentRepository.save(invoice);
                });
    }

    private String appendRemarks(String source, String remarks) {
        return remarks == null || remarks.isBlank()
                ? source
                : source + " " + remarks.trim();
    }

    private String normalizedMethod(String paymentMethod) {
        return paymentMethod == null || paymentMethod.isBlank()
                ? "Not specified"
                : paymentMethod.trim();
    }

    private String numberPrefix(DocumentType type) {
        return switch (type) {
            case QUOTATION -> "QUO";
            case PURCHASE_ORDER -> "PO";
            case INVOICE -> "INV";
            case RECEIPT -> "REC";
        };
    }

    private long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
