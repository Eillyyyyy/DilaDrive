package com.diladrive.service;

import com.diladrive.dto.DocumentDashboardDTO;
import com.diladrive.dto.DocumentDTO;
import com.diladrive.dto.QuotationRequest;
import com.diladrive.entity.Booking;
import com.diladrive.entity.Customer;
import com.diladrive.entity.Document;
import com.diladrive.entity.DocumentType;
import com.diladrive.entity.InvoiceSourceType;
import com.diladrive.entity.Payment;
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
    @org.springframework.beans.factory.annotation.Autowired private NotificationService notifications;
    @org.springframework.beans.factory.annotation.Autowired
    private TripFinanceService tripFinance;

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

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDTO> getAllDocuments() {
        return documentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(DocumentDTO::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDTO> getCustomerDocuments(Long customerId) {
        Customer customer = findCustomer(customerId);
        if (!"COMPANY".equalsIgnoreCase(customer.getCustType())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Business documents are only available to company customers.");
        }
        return documentRepository.findByCustIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(DocumentDTO::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDTO getCustomerDocument(Long customerId, Long documentId) {
        Customer customer = findCustomer(customerId);
        if (!"COMPANY".equalsIgnoreCase(customer.getCustType())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Business documents are only available to company customers.");
        }
        Document document = documentRepository
                .findByDocumentIdAndCustId(documentId, customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Document not found."));
        return DocumentDTO.from(document);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDTO getDocument(Long id) {
        return DocumentDTO.from(findDocument(id));
    }

    @Override
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

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDTO> getDocumentsByType(DocumentType type) {
        return documentRepository.findByDocumentTypeOrderByCreatedAtDesc(type)
                .stream()
                .map(DocumentDTO::from)
                .toList();
    }

    @Override
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

    @Override
    @Transactional
    public DocumentDTO createQuotation(QuotationRequest request) {
        Booking booking = findBookingForUpdate(request.bookingId());
        Customer customer = findCustomer(booking.getCustId());
        if (!"COMPANY".equalsIgnoreCase(customer.getCustType())) {
            throw conflict("Quotations are for corporate customers only.");
        }

        Document quotation = new Document();
        quotation.setDocumentType(DocumentType.QUOTATION);
        quotation.setBookingId(booking.getBookingId());
        quotation.setStatus("DRAFT");
        applyCustomerAndRequest(quotation, customer, request);
        quotation.setAmount(quotationAmount(booking,request));
        return saveWithGeneratedNumber(quotation);
    }

    @Override
    @Transactional
    public DocumentDTO updateQuotation(Long id, QuotationRequest request) {
        Document quotation = findDocumentForUpdate(id);
        requireType(quotation, DocumentType.QUOTATION);
        if (!"DRAFT".equalsIgnoreCase(quotation.getStatus())
                && !"REJECTED".equalsIgnoreCase(quotation.getStatus())) {
            throw conflict("Only draft or rejected quotations can be edited.");
        }

        Booking booking = findBookingForUpdate(request.bookingId());
        Customer customer = findCustomer(booking.getCustId());
        quotation.setBookingId(booking.getBookingId());
        quotation.setStatus("DRAFT");
        applyCustomerAndRequest(quotation, customer, request);
        quotation.setAmount(quotationAmount(booking,request));
        return DocumentDTO.from(documentRepository.save(quotation));
    }

    @Override
    @Transactional
    public DocumentDTO approveQuotation(Long id) {
        Document quotation = findDocumentForUpdate(id);
        requireType(quotation, DocumentType.QUOTATION);
        requireStatus(quotation, "DRAFT", "Only draft quotations can be approved.");
        quotation.setStatus("APPROVED");
        var approved = documentRepository.save(quotation);
        if (notifications != null) {
            notifications.createCustomerNotification(approved.getCustId(), "Quotation approved",
                    "Quotation " + approved.getDocumentNumber() + " is available in Business Documents.",
                    "QUOTATION_APPROVED");
        }
        return DocumentDTO.from(approved);
    }

    @Override
    @Transactional
    public DocumentDTO rejectQuotation(Long id) {
        Document quotation = findDocumentForUpdate(id);
        requireType(quotation, DocumentType.QUOTATION);
        requireStatus(quotation, "DRAFT", "Only draft quotations can be rejected.");
        quotation.setStatus("REJECTED");
        return DocumentDTO.from(documentRepository.save(quotation));
    }

    @Override
    @Transactional
    public DocumentDTO generatePurchaseOrder(Long quotationId) {
        throw new ResponseStatusException(HttpStatus.GONE, "Record the purchase order supplied by the customer.");
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDTO> getCustomerReceipts(Long customerId) {
        return documentRepository.findVerifiedReceipts(customerId, null).stream().map(DocumentDTO::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDTO getCustomerReceipt(Long customerId, Long documentId) {
        return documentRepository.findVerifiedReceipts(customerId, documentId).stream().findFirst()
                .map(DocumentDTO::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found."));
    }

    @Override
    @Transactional
    public DocumentDTO recordPurchaseOrder(Long quotationId, String clientReference) {
        if (clientReference == null || clientReference.isBlank() || clientReference.strip().length() > 100) {
            throw badRequest("Enter a client purchase order reference of up to 100 characters.");
        }
        return savePurchaseOrder(quotationId, clientReference.strip());
    }

    private DocumentDTO savePurchaseOrder(Long quotationId, String clientReference) {
        Document quotation = findDocumentForUpdate(quotationId);
        requireType(quotation, DocumentType.QUOTATION);
        requireStatus(quotation, "APPROVED",
                "A purchase order can only be generated from an approved quotation.");
        if (documentRepository.existsByDocumentTypeAndSourceDocumentId(
                DocumentType.PURCHASE_ORDER, quotation.getDocumentId())) {
            throw conflict("A purchase order has already been generated for this quotation.");
        }

        Document purchaseOrder = copyDocument(quotation, DocumentType.PURCHASE_ORDER);
        purchaseOrder.setSourceDocumentId(quotation.getDocumentId());
        purchaseOrder.setStatus("ISSUED");
        purchaseOrder.setRemarks(clientReference == null ? sourceRemark("Quotation", quotation)
                : "Received client PO " + clientReference + " / " + sourceRemark("Quotation", quotation));
        return saveWithGeneratedNumber(purchaseOrder);
    }

    @Override
    @Transactional
    public DocumentDTO generateInvoice(Long sourceId, InvoiceSourceType sourceType) {
        if (sourceType == null) {
            throw badRequest("Invoice source type must be PURCHASE_ORDER or BOOKING.");
        }
        Document sourceDocument = null;
        Booking booking;
        Customer customer;

        if (sourceType == InvoiceSourceType.PURCHASE_ORDER) {
            sourceDocument = findDocumentForUpdate(sourceId);
            requireType(sourceDocument, DocumentType.PURCHASE_ORDER);
            requireStatus(sourceDocument, "ISSUED",
                    "An invoice can only be generated from an issued purchase order.");
            if (documentRepository.existsByDocumentTypeAndSourceDocumentId(
                    DocumentType.INVOICE, sourceDocument.getDocumentId())) {
                throw conflict("An invoice already exists for this purchase order.");
            }
            booking = findBookingForUpdate(sourceDocument.getBookingId());
            if (documentRepository.existsByDocumentTypeAndBookingId(
                    DocumentType.INVOICE, booking.getBookingId())) {
                throw conflict("An invoice already exists for this booking.");
            }
            customer = findCustomer(sourceDocument.getCustId());
        } else {
            booking = findBookingForUpdate(sourceId);
            if (!"COMPLETED".equalsIgnoreCase(booking.getBookingStatus())) {
                throw conflict("An invoice can only be generated from a completed booking.");
            }
            if (documentRepository.existsByDocumentTypeAndBookingId(
                    DocumentType.INVOICE, booking.getBookingId())) {
                throw conflict("An invoice already exists for this booking.");
            }
            if (booking.getBookingTripfare() == null
                    || booking.getBookingTripfare().signum() < 0
                    || (booking.getBookingTripfare().signum()==0 && booking.getFareSnapshot()==null)) {
                throw conflict("The completed booking does not have a valid trip fare.");
            }
            customer = findCustomer(booking.getCustId());
        }

        Document invoice = new Document();
        var baseFare = booking.getBookingTripfare();
        var finalFare = tripFinance == null ? baseFare : tripFinance.finalFare(booking);
        boolean hasActualCharges = finalFare != null && baseFare != null && finalFare.compareTo(baseFare) != 0;
        if(sourceDocument!=null && finalFare != null && (booking.getFareSnapshot()!=null || hasActualCharges)
                && sourceDocument.getAmount().compareTo(finalFare)!=0)
            throw conflict("The purchase order uses a previous accepted fare. Create an updated quotation and purchase order before invoicing.");
        invoice.setDocumentType(DocumentType.INVOICE);
        invoice.setBookingId(booking.getBookingId());
        invoice.setSourceDocumentId(
                sourceDocument == null ? null : sourceDocument.getDocumentId());
        invoice.setStatus("ISSUED");
        invoice.setAmount(sourceDocument == null
                ? finalFare
                : sourceDocument.getAmount());
        applyCustomerSnapshot(invoice, customer);
        invoice.setRemarks(sourceDocument == null
                ? "Completed booking #" + booking.getBookingId()
                : sourceRemark("Purchase Order", sourceDocument));
        return saveWithGeneratedNumber(invoice);
    }

    @Override
    @Transactional
    public DocumentDTO markInvoicePaid(Long invoiceId) {
        Document invoice = findDocumentForUpdate(invoiceId);
        requireType(invoice, DocumentType.INVOICE);
        requireStatus(invoice, "ISSUED", "Only issued invoices can be marked as paid.");
        var verified = payments.sumVerifiedAmount(invoice.getBookingId());
        if (verified == null || verified.compareTo(invoice.getAmount()) < 0) {
            throw conflict("Verified payments do not cover this invoice.");
        }
        invoice.setStatus("PAID");
        return DocumentDTO.from(documentRepository.save(invoice));
    }

    @Override
    @Transactional
    public DocumentDTO generateReceipt(Long invoiceId) {
        throw new ResponseStatusException(HttpStatus.GONE,
                "Receipts are generated when a payment is verified.");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.diladrive.repository.PaymentRepository payments;

    @Override
    @Transactional
    public DocumentDTO generateReceiptForPayment(Payment payment) {
        if (payment == null || payment.getPaymentId() == null) {
            throw conflict("A receipt requires a saved, verified payment.");
        }
        payment = payments.findByIdForUpdate(payment.getPaymentId())
                .orElseThrow(() -> conflict("A receipt requires a saved, verified payment."));
        if (!"VERIFIED".equalsIgnoreCase(payment.getPaymentStatus())) {
            throw conflict("A receipt can only be generated for a verified payment.");
        }
        if (documentRepository.existsByDocumentTypeAndPaymentId(
                DocumentType.RECEIPT, payment.getPaymentId())) {
            return documentRepository.findByDocumentTypeAndPaymentId(
                            DocumentType.RECEIPT, payment.getPaymentId())
                    .map(DocumentDTO::from)
                    .orElseThrow(() -> conflict("The payment receipt could not be loaded."));
        }
        Booking booking = bookingRepository.findById(payment.getBooking().getBookingId())
                .orElseThrow(() -> notFound("Booking not found."));
        Customer customer = findCustomer(booking.getCustId());
        Document receipt = new Document();
        receipt.setDocumentType(DocumentType.RECEIPT);
        receipt.setBookingId(booking.getBookingId());
        receipt.setPaymentId(payment.getPaymentId());
        receipt.setStatus("PAID");
        receipt.setAmount(payment.getAmount());
        applyCustomerSnapshot(receipt, customer);
        receipt.setRemarks("Verified " + payment.getPaymentMethod().replace('_', ' ')
                + " payment for booking " + booking.getBookingId());
        DocumentDTO generated = saveWithGeneratedNumber(receipt);
        if (notifications != null) {
            notifications.createCustomerNotification(booking.getCustId(), "Receipt available",
                    "Your receipt for booking BK-%06d is available.".formatted(booking.getBookingId()),
                    "RECEIPT_AVAILABLE");
        }
        return generated;
    }

    private DocumentDTO saveWithGeneratedNumber(Document document) {
        document.setDocumentNumber(
                "TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        Document saved = documentRepository.saveAndFlush(document);
        saved.setDocumentNumber(
                numberPrefix(saved.getDocumentType()) + "-"
                        + Long.toString(saved.getDocumentId(), 36).toUpperCase(Locale.ROOT));
        Document published = documentRepository.save(saved);
        if (notifications != null && "ISSUED".equals(published.getStatus())) {
            notifications.createCustomerNotification(published.getCustId(), "Booking document available",
                    published.getDocumentNumber() + " is available in Business Documents.", "DOCUMENT");
        }
        return DocumentDTO.from(published);
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
            QuotationRequest request) {
        applyCustomerSnapshot(document, customer);
        document.setAmount(request.amount());
        document.setRemarks(normalizeRemarks(request.remarks()));
    }

    private java.math.BigDecimal quotationAmount(Booking booking,QuotationRequest request) {
        var baseFare = booking.getBookingTripfare();
        var finalFare = tripFinance == null ? baseFare : tripFinance.finalFare(booking);
        if (booking.getFareSnapshot()!=null
                || (finalFare != null && baseFare != null && finalFare.compareTo(baseFare) != 0)) return finalFare;
        if(request.amount()==null || request.amount().signum()<=0) throw badRequest("Enter a quotation amount greater than zero.");
        return request.amount();
    }

    private void applyCustomerSnapshot(Document document, Customer customer) {
        document.setCustId(customer.getCustId());
        document.setCustName(customer.getCustName());
        document.setCustEmail(customer.getCustEmail());
        document.setCustPhone(customer.getCustPhoneno());
    }

    private Booking findBookingForUpdate(Long bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> notFound("Booking not found."));
    }

    private Customer findCustomer(Long customerId) {
        if (customerId == null) {
            throw conflict("The booking does not have a customer.");
        }
        return customerRepository.findById(customerId)
                .orElseThrow(() -> notFound("Customer not found."));
    }

    private Document findDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> notFound("Document not found."));
    }

    private Document findDocumentForUpdate(Long id) {
        return documentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound("Document not found."));
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

    private String sourceRemark(String type, Document source) {
        return type + ": " + source.getDocumentNumber();
    }

    private String normalizeRemarks(String remarks) {
        return remarks == null || remarks.isBlank() ? null : remarks.trim();
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

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
