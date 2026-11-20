package com.diladrive.controller;

import com.diladrive.security.AdminSession;
import com.diladrive.service.TripFinanceService;
import com.diladrive.service.PricingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class TripFinanceController {
    private final TripFinanceService finance;
    private final PricingService pricing;

    public TripFinanceController(TripFinanceService finance, PricingService pricing) {
        this.finance = finance;
        this.pricing = pricing;
    }

    @GetMapping("/trip-pricing-settings")
    public TripFinanceService.Settings settings(HttpServletRequest request) {
        AdminSession.requireAdminId(request);
        return finance.settings();
    }

    @PutMapping("/trip-pricing-settings")
    public TripFinanceService.Settings saveSettings(
            @Valid @RequestBody TripFinanceService.Settings settings, HttpServletRequest request) {
        return finance.saveSettings(settings, AdminSession.requireAdminId(request));
    }

    @GetMapping("/bookings/{id}/charges")
    public Map<String,Object> charges(@PathVariable long id, HttpServletRequest request) {
        AdminSession.requireAdminId(request);
        return finance.charges(id);
    }

    @PutMapping("/bookings/{id}/charges")
    public Map<String,Object> saveCharges(@PathVariable long id,
            @Valid @RequestBody TripFinanceService.Charges charges, HttpServletRequest request) {
        return finance.saveCharges(id, charges, AdminSession.requireAdminId(request));
    }

    @PostMapping("/bookings/{id}/charges/preview")
    public Map<String,BigDecimal> previewCharges(@PathVariable long id,
            @Valid @RequestBody TripFinanceService.Charges charges, HttpServletRequest request) {
        AdminSession.requireAdminId(request);
        return finance.previewCharges(id, charges);
    }

    public record RoadDistance(@NotNull @DecimalMin("0.001") @Digits(integer = 6, fraction = 3)
                               BigDecimal distanceKm) {}

    @PutMapping("/bookings/{id}/road-distance")
    public Map<String,BigDecimal> confirmDistance(@PathVariable long id,
            @Valid @RequestBody RoadDistance body, HttpServletRequest request) {
        var fare = finance.confirmDistance(id, body.distanceKm(), AdminSession.requireAdminId(request), pricing);
        return Map.of("distanceKm",body.distanceKm(),"estimatedFare",fare);
    }

    @GetMapping("/driver-earnings")
    public Map<String,Object> earnings(@RequestParam @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) TripFinanceService.PaymentStatus status, HttpServletRequest request) {
        AdminSession.requireAdminId(request);
        return finance.earnings(month, driverId, status);
    }

    public record PaymentChange(@NotNull TripFinanceService.PaymentStatus status,
                                @NotBlank @Size(max = 500) String reason) {}

    @PatchMapping("/driver-earnings/{assignmentId}/payment-status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void paymentStatus(@PathVariable long assignmentId, @Valid @RequestBody PaymentChange change,
                              HttpServletRequest request) {
        finance.paymentStatus(assignmentId, change.status(), change.reason(), AdminSession.requireAdminId(request));
    }

    @GetMapping("/driver-earnings/{assignmentId}/history")
    public List<Map<String,Object>> history(@PathVariable long assignmentId, HttpServletRequest request) {
        AdminSession.requireAdminId(request);
        return finance.history(assignmentId);
    }
}
