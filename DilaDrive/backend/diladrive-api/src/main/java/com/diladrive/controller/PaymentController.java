package com.diladrive.controller;

import com.diladrive.dto.PaymentResponse;
import com.diladrive.dto.PaymentReviewRequest;
import com.diladrive.dto.CashPaymentRequest;
import com.diladrive.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/mine")
    public List<PaymentResponse> getMyPayments(HttpServletRequest request) {
        return paymentService.getCustomerPayments(requireCustomerId(request));
    }

    @PostMapping(value = "/bookings/{bookingId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse submitPayment(
            @PathVariable Long bookingId,
            @RequestParam BigDecimal amount,
            @RequestParam String paymentMethod,
            @RequestPart("proof") MultipartFile proof,
            HttpServletRequest request) {
        return paymentService.submit(requireCustomerId(request), bookingId,
                amount, paymentMethod, proof);
    }

    @PostMapping("/bookings/{bookingId}/cash")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse recordCashReceived(
            @PathVariable Long bookingId,
            @Valid @RequestBody CashPaymentRequest body,
            HttpServletRequest request) {
        var session = request.getSession(false);
        Object admin = session == null ? null : session.getAttribute("adminId");
        if (admin instanceof Long adminId) {
            return paymentService.recordCashReceived(bookingId, body.amount(), null, adminId);
        }
        return paymentService.recordCashReceived(
                bookingId, body.amount(), requireDriverId(request), null);
    }

    @GetMapping
    public List<PaymentResponse> getAllPayments(HttpServletRequest request) {
        requireAdminId(request);
        return paymentService.getAdminPayments();
    }

    @PutMapping("/{paymentId}/review")
    public PaymentResponse reviewPayment(
            @PathVariable Long paymentId,
            @Valid @RequestBody PaymentReviewRequest body,
            HttpServletRequest request) {
        return paymentService.review(paymentId, requireAdminId(request),
                body.decision(), body.reason());
    }

    @GetMapping("/{paymentId}/proof")
    public ResponseEntity<byte[]> getProof(
            @PathVariable Long paymentId, HttpServletRequest request) {
        Long adminId = adminId(request);
        Long customerId = adminId == null ? requireCustomerId(request) : null;
        var proof = paymentService.readProof(paymentId, customerId, adminId != null);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(proof.contentType()))
                .body(proof.bytes());
    }

    private static Long requireAdminId(HttpServletRequest request) {
        Long id = adminId(request);
        if (id != null) return id;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in as an admin.");
    }

    private static Long adminId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object id = session == null ? null : session.getAttribute("adminId");
        return id instanceof Long value ? value : null;
    }

    private static Long requireCustomerId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object role = session == null ? null : session.getAttribute("mobileRole");
        Object id = session == null ? null : session.getAttribute("mobileUserId");
        if (!(id instanceof Long customerId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        }
        if (!"CUSTOMER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Payments are only available to customer accounts.");
        }
        return customerId;
    }

    private static Long requireDriverId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object role = session == null ? null : session.getAttribute("mobileRole");
        Object id = session == null ? null : session.getAttribute("mobileUserId");
        if (!(id instanceof Long driverId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        }
        if (!"DRIVER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cash can only be recorded by the assigned driver or an admin.");
        }
        return driverId;
    }
}
