package com.diladrive.service;

import com.diladrive.dto.PaymentResponse;
import com.diladrive.entity.Admin;
import com.diladrive.entity.Assignment;
import com.diladrive.entity.Payment;
import com.diladrive.repository.AdminRepository;
import com.diladrive.repository.AssignmentRepository;
import com.diladrive.repository.BookingRepository;
import com.diladrive.repository.CustomerRepository;
import com.diladrive.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {
    private static final Set<String> METHODS = Set.of("BANK_TRANSFER", "CARD");
    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final CustomerRepository customers;
    private final AdminRepository admins;
    private final AssignmentRepository assignments;
    private final NotificationService notifications;
    private final DocumentService documents;
    private final Path storageDirectory;

    public PaymentService(
            PaymentRepository payments,
            BookingRepository bookings,
            CustomerRepository customers,
            AdminRepository admins,
            AssignmentRepository assignments,
            NotificationService notifications,
            DocumentService documents,
            @Value("${app.payment-proofs.storage-dir:./uploads/payment-proofs}") String storageDirectory) {
        this.payments = payments;
        this.bookings = bookings;
        this.customers = customers;
        this.admins = admins;
        this.assignments = assignments;
        this.notifications = notifications;
        this.documents = documents;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getCustomerPayments(Long customerId) {
        return payments.findByBookingCustIdOrderBySubmittedAtDesc(customerId).stream()
                .map(PaymentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getAdminPayments() {
        return payments.findAllByOrderBySubmittedAtDesc().stream().map(payment -> {
            var response = PaymentResponse.from(payment);
            String name = customers.findById(response.customerId())
                    .map(customer -> customer.getCustName()).orElse("Customer");
            return response.withCustomerName(name);
        }).toList();
    }

    @Transactional
    public PaymentResponse submit(Long customerId, Long bookingId,
            BigDecimal amount, String method, MultipartFile file) {
        var booking = bookings.findByIdForUpdate(bookingId)
                .filter(item -> customerId.equals(item.getCustId()))
                .orElseThrow(() -> notFound("Booking not found."));
        if (booking.getBookingTripfare() == null || booking.getBookingTripfare().signum() <= 0) {
            throw conflict("Admin must set the final fare before payment can be submitted.");
        }
        String bookingStatus = booking.getBookingStatus() == null ? ""
                : booking.getBookingStatus().trim().toUpperCase(Locale.ROOT);
        if (Set.of("PENDING", "PENDING_VERIFICATION", "CANCELLED", "REJECTED")
                .contains(bookingStatus)) {
            throw conflict("Payment is available after Admin confirms the booking and fare.");
        }
        if (amount == null || amount.scale() > 2 || amount.signum() <= 0) {
            throw badRequest("Enter a payment amount greater than zero with at most two decimals.");
        }
        String paymentMethod = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(paymentMethod)) throw badRequest("Choose a supported payment method.");
        if (payments.existsByBookingBookingIdAndPaymentStatus(
                bookingId, "PENDING_VERIFICATION")) {
            throw conflict("A payment for this booking is already awaiting Admin review.");
        }
        BigDecimal paid = payments.sumVerifiedAmount(bookingId);
        BigDecimal remaining = booking.getBookingTripfare().subtract(paid);
        if (remaining.signum() <= 0 || amount.compareTo(remaining) > 0) {
            throw conflict("Payment amount cannot exceed the remaining confirmed fare of RM "
                    + remaining.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP) + ".");
        }
        StoredProof proof = validateAndStore(file);
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(amount.setScale(2, RoundingMode.UNNECESSARY));
        payment.setPaymentMethod(paymentMethod);
        payment.setSurchargeAmount("CARD".equals(paymentMethod)
                ? amount.multiply(new BigDecimal("0.06")).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2));
        payment.setReceiptFileUrl(proof.path().getFileName().toString());
        try {
            Payment saved = payments.saveAndFlush(payment);
            notifications.createCustomerNotification(customerId,
                    "Payment proof submitted",
                    "Your payment proof for booking BK-%06d is awaiting Admin review."
                            .formatted(bookingId), "PAYMENT_SUBMITTED");
            return PaymentResponse.from(saved);
        } catch (RuntimeException exception) {
            deleteQuietly(proof.path());
            throw exception;
        }
    }

    @Transactional
    public PaymentResponse recordCashReceived(Long bookingId, BigDecimal amount,
            Long recordedByDriverId, Long recordedByAdminId) {
        var booking = bookings.findByIdForUpdate(bookingId)
                .orElseThrow(() -> notFound("Booking not found."));
        if (recordedByDriverId != null) {
            Assignment assignedTrip = assignments
                    .findTopByBookingBookingIdOrderByAssignmentDateDesc(bookingId)
                    .filter(item -> item.getDriver() != null
                            && recordedByDriverId.equals(item.getDriver().getDriverId())
                            )
                    .orElseThrow(() -> notFound("Booking not found."));
            if (!"COMPLETED".equalsIgnoreCase(assignedTrip.getAssignmentStatus())) {
                throw conflict("Cash can be recorded only after the trip is completed.");
            }
        }
        if (!"COMPLETED".equalsIgnoreCase(booking.getBookingStatus())) {
            throw conflict("Cash can be recorded only after the trip is completed.");
        }
        if (booking.getBookingTripfare() == null
                || booking.getBookingTripfare().signum() <= 0) {
            throw conflict("Admin must set the final fare before cash can be recorded.");
        }
        if (amount == null || amount.scale() > 2 || amount.signum() <= 0) {
            throw badRequest("Enter a cash amount greater than zero with at most two decimals.");
        }
        if (payments.existsByBookingBookingIdAndPaymentStatus(
                bookingId, "PENDING_VERIFICATION")) {
            throw conflict("A payment for this booking is already awaiting Admin review.");
        }
        BigDecimal remaining = booking.getBookingTripfare()
                .subtract(payments.sumVerifiedAmount(bookingId));
        if (remaining.signum() <= 0 || amount.compareTo(remaining) > 0) {
            throw conflict("Cash amount cannot exceed the remaining confirmed fare of RM "
                    + remaining.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP) + ".");
        }

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(amount.setScale(2, RoundingMode.UNNECESSARY));
        payment.setPaymentMethod("CASH");
        payment.setSurchargeAmount(BigDecimal.ZERO.setScale(2));
        payment.setReceiptFileUrl(null);
        payment.setRecordedByDriverId(recordedByDriverId);
        payment.setRecordedByAdminId(recordedByAdminId);
        Payment saved = payments.saveAndFlush(payment);
        notifications.createCustomerNotification(booking.getCustId(),
                "Cash payment recorded",
                "Cash received for booking BK-%06d is awaiting Admin review."
                        .formatted(bookingId), "PAYMENT_SUBMITTED");
        return PaymentResponse.from(saved);
    }

    @Transactional
    public PaymentResponse review(Long paymentId, Long adminId, String decision, String reason) {
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> notFound("Payment not found."));
        if (!"PENDING_VERIFICATION".equals(payment.getPaymentStatus())) {
            throw conflict("Only pending payments can be reviewed.");
        }
        String normalized = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("VERIFIED", "REJECTED").contains(normalized)) {
            throw badRequest("Choose VERIFIED or REJECTED.");
        }
        if ("REJECTED".equals(normalized) && (reason == null || reason.isBlank())) {
            throw badRequest("Enter a reason when rejecting payment proof.");
        }
        if ("VERIFIED".equals(normalized)) {
            BigDecimal total = payments.sumVerifiedAmount(payment.getBooking().getBookingId())
                    .add(payment.getAmount());
            if (total.compareTo(payment.getBooking().getBookingTripfare()) > 0) {
                throw conflict("This payment would exceed the confirmed booking fare.");
            }
            payment.setVerifiedAt(Instant.now());
            Admin admin = admins.findById(adminId)
                    .orElseThrow(() -> notFound("Admin account not found."));
            payment.setVerifiedBy(admin);
            payment.setPaymentStatus("VERIFIED");
            payment.setRejectReason(null);
        } else {
            payment.setPaymentStatus("REJECTED");
            payment.setRejectReason(reason.trim());
        }
        Payment saved = payments.saveAndFlush(payment);
        Long customerId = payment.getBooking().getCustId();
        if ("VERIFIED".equals(normalized)) {
            documents.generateReceiptForPayment(saved);
            notifications.createCustomerNotification(customerId,
                    "Payment verified", "Your payment for booking BK-%06d was verified."
                            .formatted(payment.getBooking().getBookingId()), "PAYMENT_VERIFIED");
        } else {
            notifications.createCustomerNotification(customerId,
                    "Payment proof needs attention", "Your payment proof for booking BK-%06d "
                            .formatted(payment.getBooking().getBookingId())
                            + " was rejected: " + reason.trim(), "PAYMENT_REJECTED");
        }
        return PaymentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public StoredProof readProof(Long paymentId, Long customerId, boolean admin) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> notFound("Payment not found."));
        if (!admin && !customerId.equals(payment.getBooking().getCustId())) {
            throw notFound("Payment not found.");
        }
        if (payment.getReceiptFileUrl() == null || payment.getReceiptFileUrl().isBlank()) {
            throw notFound("No uploaded proof is attached to this payment.");
        }
        String filename = Path.of(payment.getReceiptFileUrl()).getFileName().toString();
        Path path = storageDirectory.resolve(filename).normalize();
        if (!path.startsWith(storageDirectory) || !Files.isRegularFile(path)) {
            throw notFound("Payment proof file not found.");
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return new StoredProof(bytes, contentType(bytes), path);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The payment proof could not be read.", exception);
        }
    }

    private StoredProof validateAndStore(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
            throw badRequest("Choose a JPEG, PNG or PDF payment proof smaller than 10 MB.");
        }
        try {
            byte[] bytes = file.getBytes();
            String type = contentType(bytes);
            if (type == null) throw badRequest("The selected file is not a valid JPEG, PNG or PDF.");
            String extension = switch (type) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                default -> ".pdf";
            };
            if ("image/jpeg".equals(type) || "image/png".equals(type)) {
                try (var input = javax.imageio.ImageIO.createImageInputStream(
                        new java.io.ByteArrayInputStream(bytes))) {
                    if (input == null || !javax.imageio.ImageIO.getImageReaders(input).hasNext()) {
                        throw badRequest("The selected image file is invalid.");
                    }
                }
            }
            Path path = storageDirectory.resolve(UUID.randomUUID() + extension).normalize();
            if (!path.startsWith(storageDirectory)) throw badRequest("Invalid payment proof path.");
            Files.createDirectories(storageDirectory);
            Files.write(path, bytes);
            return new StoredProof(bytes, type, path);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The payment proof could not be stored.", exception);
        }
    }

    private String contentType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) return "image/png";
        if (bytes.length >= 5 && "%PDF-".equals(new String(bytes, 0, 5,
                java.nio.charset.StandardCharsets.US_ASCII))) return "application/pdf";
        return null;
    }

    private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }
    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record StoredProof(byte[] bytes, String contentType, Path path) { }
}
