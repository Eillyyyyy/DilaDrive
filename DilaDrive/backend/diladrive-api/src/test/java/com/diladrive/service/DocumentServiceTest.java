package com.diladrive.service;

import com.diladrive.dto.QuotationRequest;
import com.diladrive.entity.Booking;
import com.diladrive.entity.Customer;
import com.diladrive.entity.Document;
import com.diladrive.entity.DocumentType;
import com.diladrive.entity.InvoiceSourceType;
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

        var result = service.createQuotation(new QuotationRequest(
                15L,
                new BigDecimal("125.50"),
                "Transport service"));

        assertEquals("QUO-2T", result.documentNumber());
        assertEquals("Aina Logistics", result.custName());
        assertEquals("DRAFT", result.status());
        assertEquals(new BigDecimal("125.50"), result.amount());
    }

    @Test
    void derivesQuotationCustomerFromBooking() {
        when(bookingRepository.findByIdForUpdate(15L))
                .thenReturn(Optional.of(booking(15L, 7L)));
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer(7L)));
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setDocumentId(101L);
            return document;
        });
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        var result = service.createQuotation(new QuotationRequest(
                15L,
                new BigDecimal("125.50"),
                null));

        assertEquals(7L, result.custId());
        assertEquals("Aina Logistics", result.custName());
    }

    @Test
    void generatesPurchaseOrderOnlyFromApprovedQuotation() {
        Document quotation = document(42L, DocumentType.QUOTATION, "QUO-16", "APPROVED");
        when(documentRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(quotation));
        when(documentRepository.existsByDocumentTypeAndSourceDocumentId(
                DocumentType.PURCHASE_ORDER, 42L)).thenReturn(false);
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setDocumentId(102L);
            return document;
        });
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        var result = service.generatePurchaseOrder(42L);

        assertEquals(DocumentType.PURCHASE_ORDER, result.documentType());
        assertEquals("ISSUED", result.status());
        assertEquals(42L, result.sourceDocumentId());
        assertEquals("Quotation: QUO-16", result.remarks());
    }

    @Test
    void createsInvoiceFromCompletedBookingAndBookingFare() {
        Booking booking = booking(15L, 7L);
        booking.setBookingStatus("COMPLETED");
        booking.setBookingTripfare(new BigDecimal("250.00"));
        when(bookingRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(booking));
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer(7L)));
        when(documentRepository.existsByDocumentTypeAndBookingId(DocumentType.INVOICE, 15L))
                .thenReturn(false);
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setDocumentId(104L);
            return document;
        });
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        var result = service.generateInvoice(15L, InvoiceSourceType.BOOKING);

        assertEquals(DocumentType.INVOICE, result.documentType());
        assertEquals("ISSUED", result.status());
        assertEquals(new BigDecimal("250.00"), result.amount());
        assertEquals(7L, result.custId());
    }

    @Test
    void invoiceCanOnlyBeGeneratedFromCompletedBooking() {
        Booking booking = booking(15L, 7L);
        booking.setBookingStatus("CONFIRMED");
        when(bookingRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(booking));

        assertThrows(ResponseStatusException.class, () ->
                service.generateInvoice(15L, InvoiceSourceType.BOOKING));
        verify(documentRepository, never()).saveAndFlush(any(Document.class));
    }

    @Test
    void onlyPaidInvoiceCanGenerateReceipt() {
        Document invoice = document(55L, DocumentType.INVOICE, "INV-1", "ISSUED");
        when(documentRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(invoice));

        assertThrows(ResponseStatusException.class, () -> service.generateReceipt(55L));
        verify(documentRepository, never()).saveAndFlush(any(Document.class));

        invoice.setStatus("PAID");
        when(documentRepository.existsByDocumentTypeAndSourceDocumentId(
                DocumentType.RECEIPT, 55L)).thenReturn(false);
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> {
            Document receipt = invocation.getArgument(0);
            receipt.setDocumentId(103L);
            return receipt;
        });
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        var result = service.generateReceipt(55L);

        assertEquals(DocumentType.RECEIPT, result.documentType());
        assertEquals("PAID", result.status());
        assertEquals(55L, result.sourceDocumentId());
        assertEquals("Invoice: INV-1", result.remarks());
    }

    @Test
    void marksIssuedInvoicePaid() {
        Document invoice = document(55L, DocumentType.INVOICE, "INV-1", "ISSUED");
        when(documentRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(invoice));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        var result = service.markInvoicePaid(55L);

        assertEquals("PAID", result.status());
    }

    @Test
    void rejectsReceiptForUnpaidInvoice() {
        Document invoice = document(55L, DocumentType.INVOICE, "INV-1", "ISSUED");
        when(documentRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(invoice));
        assertThrows(ResponseStatusException.class, () -> service.generateReceipt(55L));
    }

    @Test
    void listsOnlyDocumentsOwnedByCompanyCustomer() {
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer(7L)));
        when(documentRepository.findByCustIdOrderByCreatedAtDesc(7L))
                .thenReturn(java.util.List.of(
                        document(88L, DocumentType.INVOICE, "INV-88", "ISSUED")));

        var documents = service.getCustomerDocuments(7L);

        assertEquals(1, documents.size());
        assertEquals("INV-88", documents.get(0).documentNumber());
        verify(documentRepository).findByCustIdOrderByCreatedAtDesc(7L);
    }

    @Test
    void rejectsBusinessDocumentAccessForNormalCustomer() {
        Customer customer = customer(9L);
        customer.setCustType("NORMAL");
        when(customerRepository.findById(9L)).thenReturn(Optional.of(customer));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getCustomerDocuments(9L));

        assertEquals(403, error.getStatusCode().value());
        verify(documentRepository, never()).findByCustIdOrderByCreatedAtDesc(9L);
    }

    @Test
    void customerCanOnlyReadOwnedBusinessDocument() {
        Document invoice = document(88L, DocumentType.INVOICE, "INV-88", "ISSUED");
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer(7L)));
        when(documentRepository.findByDocumentIdAndCustId(88L, 7L))
                .thenReturn(Optional.of(invoice));

        var result = service.getCustomerDocument(7L, 88L);

        assertEquals("INV-88", result.documentNumber());
        verify(documentRepository).findByDocumentIdAndCustId(88L, 7L);
    }

    @Test
    void customerCannotReadAnotherCustomersDocument() {
        when(customerRepository.findById(9L)).thenReturn(Optional.of(customer(9L)));
        when(documentRepository.findByDocumentIdAndCustId(88L, 9L))
                .thenReturn(Optional.empty());

        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.getCustomerDocument(9L, 88L));

        assertEquals(404, error.getStatusCode().value());
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
        customer.setCustType("COMPANY");
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
