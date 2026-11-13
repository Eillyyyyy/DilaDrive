package com.diladrive.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_method", length = 24, nullable = false)
    private String paymentMethod;

    @Column(name = "surcharge_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal surchargeAmount = BigDecimal.ZERO;

    @Column(name = "receipt_file_url", length = 255)
    private String receiptFileUrl;

    @Column(name = "payment_status", length = 24, nullable = false)
    private String paymentStatus = "PENDING_VERIFICATION";

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private Admin verifiedBy;

    @Column(name = "recorded_by_driver_id")
    private Long recordedByDriverId;

    @Column(name = "recorded_by_admin_id")
    private Long recordedByAdminId;

    @PrePersist
    void setSubmittedAt() {
        if (submittedAt == null) submittedAt = Instant.now();
    }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Booking getBooking() { return booking; }
    public void setBooking(Booking booking) { this.booking = booking; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public BigDecimal getSurchargeAmount() { return surchargeAmount; }
    public void setSurchargeAmount(BigDecimal surchargeAmount) { this.surchargeAmount = surchargeAmount; }
    public String getReceiptFileUrl() { return receiptFileUrl; }
    public void setReceiptFileUrl(String receiptFileUrl) { this.receiptFileUrl = receiptFileUrl; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public Admin getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(Admin verifiedBy) { this.verifiedBy = verifiedBy; }
    public Long getRecordedByDriverId() { return recordedByDriverId; }
    public void setRecordedByDriverId(Long recordedByDriverId) { this.recordedByDriverId = recordedByDriverId; }
    public Long getRecordedByAdminId() { return recordedByAdminId; }
    public void setRecordedByAdminId(Long recordedByAdminId) { this.recordedByAdminId = recordedByAdminId; }
}
