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
    private final AssignmentServiceImpl service;
    public BookingConfirmationController(AssignmentServiceImpl service) { this.service = service; }

    @GetMapping("/{id}/assignment-options")
    public java.util.List<AssignmentServiceImpl.AssignmentOption> options(@PathVariable Long id,
            @RequestParam(required=false) java.time.LocalDateTime scheduledEndAt, HttpServletRequest request) {
        AdminSession.requireAdminId(request);
        return service.options(id,scheduledEndAt);
    }

    @PostMapping("/{id}/confirm-and-assign")
    public AssignmentResponse confirm(@PathVariable Long id,
            @Valid @RequestBody BookingConfirmationRequest body, HttpServletRequest request) {
        AdminSession.requireAdminId(request);
        return service.confirmAndAssign(new AssignmentRequest(id, body.driverId(),
                body.vehicleId(), body.scheduledEndAt()), body.tripFare());
    }
}
