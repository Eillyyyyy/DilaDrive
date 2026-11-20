package com.diladrive.controller;

import com.diladrive.security.AdminSession;
import com.diladrive.service.PricingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/pricing")
public class PricingController {
    private final PricingService pricing;
    public PricingController(PricingService pricing) { this.pricing=pricing; }
    @GetMapping public Map<String,Object> list(HttpServletRequest request) {
        AdminSession.requireAdminId(request);
        return Map.of("categories",pricing.categories(),"pricing",pricing.list(),"currency","MYR");
    }
    @GetMapping("/{category}") public PricingService.Rate get(@PathVariable String category,HttpServletRequest request) {
        AdminSession.requireAdminId(request);return pricing.get(category);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public PricingService.Rate create(@Valid @RequestBody PricingService.Input body,HttpServletRequest request) {
        AdminSession.requireAdminId(request);return pricing.create(body);
    }
    @PutMapping("/{category}") public PricingService.Rate update(@PathVariable String category,@Valid @RequestBody PricingService.Input body,HttpServletRequest request) {
        AdminSession.requireAdminId(request);return pricing.update(category,body);
    }
    public record Status(@NotNull Boolean active) {}
    @PatchMapping("/{category}/status") public PricingService.Rate status(@PathVariable String category,@Valid @RequestBody Status body,HttpServletRequest request) {
        AdminSession.requireAdminId(request);return pricing.active(category,body.active());
    }
}
