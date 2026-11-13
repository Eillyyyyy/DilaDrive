package com.diladrive.dto;

import com.diladrive.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long paymentId,
        Long bookingId,
        String bookingReference,
        Long customerId,
        String customerName,
        BigDecimal bookingFare,
        BigDecimal amount,
        BigDecimal surchargeAmount,
        String paymentMethod,
        String paymentStatus,
        String rejectReason,
        String proofUrl,
        Instant submittedAt,
        Instant verifiedAt,
        Long recordedByDriverId,
        Long recordedByAdminId) {
    public static PaymentResponse from(Payment payment) {
        var booking = payment.getBooking();
        return new PaymentResponse(
                payment.getPaymentId(), booking.getBookingId(),
                "BK-%06d".formatted(booking.getBookingId()), booking.getCustId(), null,
                booking.getBookingTripfare(), payment.getAmount(), payment.getSurchargeAmount(),
                payment.getPaymentMethod(), payment.getPaymentStatus(), payment.getRejectReason(),
                payment.getReceiptFileUrl() == null ? null
                        : "/api/payments/" + payment.getPaymentId() + "/proof",
                payment.getSubmittedAt(), payment.getVerifiedAt(),
                payment.getRecordedByDriverId(), payment.getRecordedByAdminId());
    }

    public PaymentResponse withCustomerName(String name) {
        return new PaymentResponse(paymentId, bookingId, bookingReference, customerId, name,
                bookingFare, amount, surchargeAmount, paymentMethod, paymentStatus,
                rejectReason, proofUrl, submittedAt, verifiedAt,
                recordedByDriverId, recordedByAdminId);
    }
}
