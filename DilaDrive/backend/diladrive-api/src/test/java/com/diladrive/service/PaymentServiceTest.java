package com.diladrive.service;

import com.diladrive.entity.Admin;
import com.diladrive.entity.Assignment;
import com.diladrive.entity.Booking;
import com.diladrive.entity.Driver;
import com.diladrive.entity.Payment;
import com.diladrive.repository.AdminRepository;
import com.diladrive.repository.AssignmentRepository;
import com.diladrive.repository.BookingRepository;
import com.diladrive.repository.CustomerRepository;
import com.diladrive.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final AdminRepository admins = mock(AdminRepository.class);
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final PaymentService service = new PaymentService(
            payments, bookings, customers, admins, assignments, notifications, documents,
            System.getProperty("java.io.tmpdir") + "/diladrive-payment-tests");

    @Test
    void verifyingPaymentCreatesReceiptAndNotifiesCustomer() {
        Payment payment = payment();
        Admin admin = new Admin();
        admin.setId(5L);
        when(payments.findByIdForUpdate(12L)).thenReturn(Optional.of(payment));
        when(payments.sumVerifiedAmount(8L)).thenReturn(BigDecimal.ZERO);
        when(admins.findById(5L)).thenReturn(Optional.of(admin));
        when(payments.saveAndFlush(payment)).thenReturn(payment);

        var result = service.review(12L, 5L, "VERIFIED", null);

        assertEquals("VERIFIED", result.paymentStatus());
        assertEquals(admin, payment.getVerifiedBy());
        assertNotNull(payment.getVerifiedAt());
        verify(documents).generateReceiptForPayment(payment);
        verify(notifications).createCustomerNotification(7L, "Payment verified",
                "Your payment for booking BK-000008 was verified.", "PAYMENT_VERIFIED");
    }

    @Test
    void rejectingPaymentRequiresReasonAndLeavesPaymentPending() {
        Payment payment = payment();
        when(payments.findByIdForUpdate(12L)).thenReturn(Optional.of(payment));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.review(12L, 5L, "REJECTED", "  "));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("PENDING_VERIFICATION", payment.getPaymentStatus());
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void customerCannotReadAnotherCustomersProof() {
        Payment payment = payment();
        when(payments.findById(12L)).thenReturn(Optional.of(payment));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.readProof(12L, 88L, false));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void onlyPendingPaymentCanBeReviewed() {
        Payment payment = payment();
        payment.setPaymentStatus("VERIFIED");
        when(payments.findByIdForUpdate(12L)).thenReturn(Optional.of(payment));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.review(12L, 5L, "REJECTED", "incorrect amount"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void paymentSubmissionCannotTargetAnotherCustomersBooking() {
        Booking booking = new Booking();
        booking.setCustId(88L);
        when(bookings.findByIdForUpdate(19L)).thenReturn(Optional.of(booking));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.submit(7L, 19L, new BigDecimal("10.00"),
                        "BANK_TRANSFER", null));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(payments, never()).save(any());
    }

    @Test
    void assignedDriverCanRecordCashAfterCompletedTripForAdminReview() {
        Booking booking = booking(7L, 13L, "COMPLETED");
        when(bookings.findByIdForUpdate(8L)).thenReturn(Optional.of(booking));
        when(assignments.findTopByBookingBookingIdOrderByAssignmentDateDesc(8L))
                .thenReturn(Optional.of(assignment(13L, "COMPLETED")));
        when(payments.sumVerifiedAmount(8L)).thenReturn(new BigDecimal("100.00"));
        when(payments.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            saved.setPaymentId(15L);
            return saved;
        });

        var result = service.recordCashReceived(8L, new BigDecimal("50.00"), 13L, null);

        assertEquals("CASH", result.paymentMethod());
        assertEquals("PENDING_VERIFICATION", result.paymentStatus());
        assertNull(result.proofUrl());
        assertEquals(13L, result.recordedByDriverId());
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(paymentCaptor.capture());
        assertEquals(13L, paymentCaptor.getValue().getRecordedByDriverId());
        verify(notifications).createCustomerNotification(7L,
                "Cash payment recorded",
                "Cash received for booking BK-000008 is awaiting Admin review.",
                "PAYMENT_SUBMITTED");
    }

    @Test
    void driverCannotRecordCashForAnotherDriversBooking() {
        Booking booking = booking(7L, 13L, "COMPLETED");
        when(bookings.findByIdForUpdate(8L)).thenReturn(Optional.of(booking));
        when(assignments.findTopByBookingBookingIdOrderByAssignmentDateDesc(8L))
                .thenReturn(Optional.of(assignment(13L, "COMPLETED")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.recordCashReceived(8L, new BigDecimal("50.00"), 14L, null));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(payments, never()).save(any());
    }

    @Test
    void cashCannotBeRecordedBeforeTripIsCompleted() {
        Booking booking = booking(7L, 13L, "DROP");
        when(bookings.findByIdForUpdate(8L)).thenReturn(Optional.of(booking));
        when(assignments.findTopByBookingBookingIdOrderByAssignmentDateDesc(8L))
                .thenReturn(Optional.of(assignment(13L, "DROP")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.recordCashReceived(8L, new BigDecimal("50.00"), 13L, null));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(payments, never()).save(any());
    }

    private Payment payment() {
        Booking booking = booking(7L, 13L, "CONFIRMED");
        Payment payment = new Payment();
        payment.setPaymentId(12L);
        payment.setBooking(booking);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setSurchargeAmount(new BigDecimal("6.00"));
        payment.setPaymentMethod("CARD");
        payment.setPaymentStatus("PENDING_VERIFICATION");
        return payment;
    }

    private Booking booking(Long customerId, Long driverId, String status) {
        Booking booking = new Booking();
        booking.setCustId(customerId);
        booking.setDriverId(driverId);
        booking.setBookingTripfare(new BigDecimal("250.00"));
        booking.setBookingStatus(status);
        try {
            var field = Booking.class.getDeclaredField("bookingId");
            field.setAccessible(true);
            field.set(booking, 8L);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
        return booking;
    }

    private Assignment assignment(Long driverId, String status) {
        Assignment assignment = new Assignment();
        Driver driver = new Driver();
        driver.setDriverId(driverId);
        assignment.setDriver(driver);
        assignment.setAssignmentStatus(status);
        return assignment;
    }
}
