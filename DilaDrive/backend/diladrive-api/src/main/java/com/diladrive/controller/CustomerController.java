package com.diladrive.controller;

import com.diladrive.entity.Customer;
import com.diladrive.dto.CustomerProfileResponse;
import com.diladrive.dto.CustomerProfileUpdateRequest;
import com.diladrive.service.CustomerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(
            CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<Customer> getAllCustomers(HttpServletRequest request) {
        requireAdminId(request);
        return customerService.getAllCustomers();
    }

    @PostMapping
    public ResponseEntity<?> createCustomer(
            @RequestBody Customer customer,
            HttpServletRequest request) {
        requireAdminId(request);
        try {
            return ResponseEntity.ok(customerService.createCustomer(customer));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCustomer(
            @PathVariable Long id,
            @RequestBody Customer customer,
            HttpServletRequest request) {
        requireAdminId(request);
        try {
            return ResponseEntity.ok(customerService.updateCustomer(id, customer));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public String deleteCustomer(
            @PathVariable Long id,
            HttpServletRequest request) {
        requireAdminId(request);

        customerService.deleteCustomer(id);

        return "Customer Deleted";
    }

    @GetMapping("/me")
    public CustomerProfileResponse getMyProfile(HttpServletRequest request) {
        return customerService.getCustomerProfile(requireCustomerId(request));
    }

    @PutMapping("/me")
    public CustomerProfileResponse updateMyProfile(
            @Valid @RequestBody CustomerProfileUpdateRequest profile,
            HttpServletRequest request) {
        return customerService.updateCustomerProfile(
                requireCustomerId(request), profile);
    }

    private static Long requireAdminId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object adminId = session == null ? null : session.getAttribute("adminId");
        if (adminId instanceof Long id) return id;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Please sign in as an admin.");
    }

    private static Long requireCustomerId(HttpServletRequest request) {
        var session = request.getSession(false);
        Object role = session == null ? null : session.getAttribute("mobileRole");
        Object userId = session == null ? null : session.getAttribute("mobileUserId");
        if (!(userId instanceof Long id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Please sign in again.");
        }
        if (!"CUSTOMER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Profile details are only available to customer accounts.");
        }
        return id;
    }
}
