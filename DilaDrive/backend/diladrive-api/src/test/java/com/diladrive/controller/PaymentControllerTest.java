package com.diladrive.controller;

import com.diladrive.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentControllerTest {
    private final PaymentService service = mock(PaymentService.class);
    private final PaymentController controller = new PaymentController(service);

    @Test
    void customerSubmissionUsesSessionOwnerAndTripId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileRole", "CUSTOMER");
        request.getSession().setAttribute("mobileUserId", 42L);
        var proof = new MockMultipartFile("proof", "receipt.png", "image/png",
                new byte[] {1, 2, 3});

        controller.submitPayment(17L, new BigDecimal("50.00"),
                "BANK_TRANSFER", proof, request);

        verify(service).submit(42L, 17L, new BigDecimal("50.00"),
                "BANK_TRANSFER", proof);
    }

    @Test
    void driverCannotSubmitCustomerPayment() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileRole", "DRIVER");
        request.getSession().setAttribute("mobileUserId", 13L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.getMyPayments(request));

        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void paymentReviewRequiresAdminSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileRole", "CUSTOMER");
        request.getSession().setAttribute("mobileUserId", 42L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.reviewPayment(2L,
                        new com.diladrive.dto.PaymentReviewRequest("VERIFIED", null), request));

        assertEquals(401, error.getStatusCode().value());
    }

    @Test
    void adminListUsesAdminSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("adminId", 5L);

        controller.getAllPayments(request);

        verify(service).getAdminPayments();
    }

    @Test
    void cashRecordingUsesTheSignedInDriverIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileRole", "DRIVER");
        request.getSession().setAttribute("mobileUserId", 13L);

        controller.recordCashReceived(17L,
                new com.diladrive.dto.CashPaymentRequest(new BigDecimal("40.00")), request);

        verify(service).recordCashReceived(17L, new BigDecimal("40.00"), 13L, null);
    }

    @Test
    void customerCannotRecordCashForACompletedBooking() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("mobileRole", "CUSTOMER");
        request.getSession().setAttribute("mobileUserId", 42L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.recordCashReceived(17L,
                        new com.diladrive.dto.CashPaymentRequest(new BigDecimal("40.00")), request));

        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void adminCanRecordCashForAdminReview() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("adminId", 5L);

        controller.recordCashReceived(17L,
                new com.diladrive.dto.CashPaymentRequest(new BigDecimal("40.00")), request);

        verify(service).recordCashReceived(17L, new BigDecimal("40.00"), null, 5L);
    }
}
