package com.diladrive.service;

import com.diladrive.dto.CustomerVerificationRequest;
import com.diladrive.dto.CustomerVerificationResponse;
import com.diladrive.repository.AdminRepository;
import com.diladrive.repository.CustomerRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerVerificationService {
    private final AdminRepository admins;
    private final CustomerRepository customers;
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;

    public CustomerVerificationService(AdminRepository admins, CustomerRepository customers,
            NotificationService notifications, JdbcTemplate jdbc) {
        this.admins = admins;
        this.customers = customers;
        this.notifications = notifications;
        this.jdbc = jdbc;
    }

    @Transactional
    public CustomerVerificationResponse verify(Long adminId, Long customerId, CustomerVerificationRequest request) {
        admins.findById(adminId)
                .filter(admin -> "ACTIVE".equalsIgnoreCase(admin.getStatus()))
                .filter(admin -> "ADMIN".equals(admin.getRole()) || "SUPER_ADMIN".equals(admin.getRole()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "An active administrator account is required."));

        boolean approve = "APPROVE".equals(request.decision());
        String reason = request.reason() == null ? null : request.reason().strip();
        if (approve && reason != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approval cannot include a rejection reason.");
        }
        if (!approve && (reason == null || reason.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a rejection reason.");
        }

        var customer = customers.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer account not found."));
        String previousStatus = customer.getCustStatus();
        if (!"PENDING".equalsIgnoreCase(previousStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending customer accounts can be verified.");
        }

        String status = approve ? "ACTIVE" : "REJECTED";
        Instant decidedAt = Instant.now();
        customer.recordVerification(status, reason, decidedAt, adminId);
        jdbc.update("""
                insert into customer_verification_history
                    (customer_id, admin_id, decision, previous_status, new_status, rejection_reason, decided_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, customerId, adminId, request.decision(), previousStatus, status, reason,
                OffsetDateTime.ofInstant(decidedAt, ZoneOffset.UTC));

        String title = approve ? "Account Approved" : "Account Verification Rejected";
        String message = approve ? "Your DilaDrive account has been approved. You can now make bookings."
                : "Your DilaDrive account was rejected. Reason: " + reason;
        notifications.createCustomerNotification(customerId, title, message, "ACCOUNT_VERIFICATION");
        return new CustomerVerificationResponse(customerId, customer.getCustType(), status,
                request.decision(), decidedAt, adminId, reason);
    }
}
