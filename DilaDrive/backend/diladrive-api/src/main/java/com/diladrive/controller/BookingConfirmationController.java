package com.diladrive.controller;

import com.diladrive.dto.BookingConfirmationRequest;
import com.diladrive.dto.AssignmentRequest;
import com.diladrive.dto.AssignmentResponse;
import com.diladrive.security.AdminSession;
import com.diladrive.service.AssignmentServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingConfirmationController {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BookingConfirmationController.class);
    private final AssignmentServiceImpl service;
    public BookingConfirmationController(AssignmentServiceImpl service) { this.service = service; }

    @GetMapping("/{id}/assignment-options")
    public java.util.List<AssignmentServiceImpl.AssignmentOption> options(@PathVariable Long id,
            @RequestParam(required=false) java.time.LocalDateTime scheduledEndAt, HttpServletRequest request) {
        AdminSession.requireAdminId(request);
        return service.options(id,scheduledEndAt);
    }

    @GetMapping("/{id}/assignment-preview")
    public AssignmentServiceImpl.AssignmentPreview preview(@PathVariable Long id,
            @RequestParam(required = false) java.time.LocalDateTime scheduledEndAt,
            @RequestParam(required = false) Long replacementOf, HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null && session.getAttribute("adminId") == null
                && session.getAttribute("mobileUserId") instanceof Long) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "This preview is available to Admin accounts.");
        }
        AdminSession.requireAdminId(request);
        if (id <= 0 || (replacementOf != null && replacementOf <= 0)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid booking or assignment ID.");
        }
        return service.preview(id, scheduledEndAt, replacementOf);
    }
    @PutMapping("/{id}/suggested-assignment")
    public AssignmentServiceImpl.AssignmentPreview changeSuggestion(@PathVariable Long id,
            @Valid @RequestBody AssignmentRequest body, HttpServletRequest request) {
        AdminSession.requireAdminId(request);
        return service.changeSuggestedAssignment(new AssignmentRequest(id, body.driverId(), body.vehicleId(), body.scheduledEndAt()));
    }

    @PostMapping("/{id}/confirm-and-assign")
    public AssignmentResponse confirm(@PathVariable Long id,
            @Valid @RequestBody BookingConfirmationRequest body, HttpServletRequest request) {
        Long adminId = AdminSession.requireAdminId(request);
        AssignmentResponse assignment = service.confirmAndAssign(new AssignmentRequest(id, body.driverId(),
                body.vehicleId(), body.scheduledEndAt()), null);
        logger.info("Booking assignment confirmed: adminId={}, bookingId={}, assignmentId={}, driverId={}, vehicleId={}",
                adminId, id, assignment.assignmentId(), assignment.driverId(), assignment.vehicleId());
        return assignment;
    }
}
