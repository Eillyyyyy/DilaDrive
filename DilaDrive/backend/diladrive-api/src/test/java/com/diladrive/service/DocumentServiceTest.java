package com.diladrive.service;

import com.diladrive.dto.DocumentRequest;
import com.diladrive.dto.InvoiceGenerationRequest;
import com.diladrive.dto.ReceiptGenerationRequest;
import com.diladrive.entity.Booking;
import com.diladrive.entity.Customer;
import com.diladrive.entity.Document;
import com.diladrive.entity.DocumentType;
import com.diladrive.repository.BookingRepository;
import com.diladrive.repository.CustomerRepository;
import com.diladrive.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final DocumentServiceImpl service = new DocumentServiceImpl(
            documentRepository,
            bookingRepository,
            customerRepository);

    @Test
    void createsQuotationWithCustomerSnapshotAndGeneratedNumber() {
        Booking booking = booking(15L, 7L);
        Customer customer = customer(7L);
        when(bookingRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(booking));
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setDocumentId(101L);
            return document;
        });
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        var result = service.createQuotation(new DocumentRequest(
                DocumentType.QUOTATION,
                15L,
                7L,
                new BigDecimal("125.50"),
                "Transport service"));

        assertEquals("QUO-2T", result.documentNumber());
        assertEquals("Aina Logistics", result.custName());
        assertEquals("DRAFT", result.status());
        assertEquals(new BigDecimal("125.50"), result.amount());
    }

    @Test
    void rejectsQuotationWhenCustomerDoesNotOwnBooking() {
        when(bookingRepository.findByIdForUpdate(15L))
                .thenReturn(Optional.of(booking(15L, 7L)));
        when(customerRepository.findById(8L)).thenReturn(Optional.of(customer(8L)));

        assertThrows(ResponseStatusException.class, () -> service.createQuotation(
                new DocumentRequest(
                        DocumentType.QUOTATION,
                        15L,
                        8L,
                        new BigDecimal("125.50"),
                        null)));

        verify(documentRepository, never()).saveAndFlush(any(Document.class));
    }

    @Test
    void generatesPurchaseOrderOnlyFromApprovedQuotation() {
        Document quotation = document(42L, DocumentType.QUOTATION, "QUO-16", "APPROVED");
        when(documentRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(quotation));
        when(documentRepository.existsByDocumentTypeAndRemarksStartingWith(
                DocumentType.PURCHASE_ORDER, "Quotation: QUO-16;")).thenReturn(false);
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setDocumentId(102L);
            return document;
        });
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        var result = service.generatePurchaseOrder(42L);

        assertEquals(DocumentType.PURCHASE_ORDER, result.documentType());
        assertEquals("GENERATED", result.status());
        assertEquals("QUO-16", result.remarks().substring(
                "Quotation: ".length(),
                result.remarks().indexOf(';')));
    }

    @Test
    void requiresPaymentConfirmationBeforeCreatingReceiptAndMarksInvoicePaid() {
        Document invoice = document(55L, DocumentType.INVOICE, "INV-1", "ISSUED");
        when(documentRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(invoice));

        assertThrows(ResponseStatusException.class, () -> service.generateReceipt(
                55L,
                new ReceiptGenerationRequest(false, "PAY-123", "Bank transfer")));
        verify(documentRepository, never()).saveAndFlush(any(Document.class));

        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> {
            Document receipt = invocation.getArgument(0);
            receipt.setDocumentId(103L);
            return receipt;
        });
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        var result = service.generateReceipt(
                55L,
                new ReceiptGenerationRequest(true, "PAY-123", "Bank transfer"));

        assertEquals(DocumentType.RECEIPT, result.documentType());
        assertEquals("PAID", invoice.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(result.remarks().contains("PAY-123"));
    }

    private Booking booking(Long bookingId, Long customerId) {
        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setCustId(customerId);
        return booking;
    }

    private Customer customer(Long id) {
        Customer customer = new Customer();
        customer.setCustId(id);
        customer.setCustName("Aina Logistics");
        customer.setCustEmail("aina@example.com");
        customer.setCustPhoneno("0123456789");
        return customer;
    }

    private Document document(Long id, DocumentType type, String number, String status) {
        Document document = new Document();
        document.setDocumentId(id);
        document.setDocumentType(type);
        document.setDocumentNumber(number);
        document.setBookingId(15L);
        document.setCustId(7L);
        document.setCustName("Aina Logistics");
        document.setAmount(new BigDecimal("125.50"));
        document.setStatus(status);
        return document;
    }
}
