package com.diladrive.service;

import com.diladrive.entity.Customer;
import com.diladrive.repository.CustomerRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.diladrive.dto.CustomerProfileResponse;
import com.diladrive.dto.CustomerProfileUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Customer createCustomer(Customer customer) {
        customer.setCustPassword(encodePassword(customer.getCustPassword()));
        if (customer.getCustStatus() == null) {
            customer.setCustStatus("PENDING");
        }
        return customerRepository.save(customer);
    }

    public Customer updateCustomer(Long id, Customer customer) {

        Customer existing =
                customerRepository.findById(id)
                        .orElseThrow();

        existing.setCustName(customer.getCustName());
        existing.setCustEmail(customer.getCustEmail());
        if (customer.getCustPassword() != null
                && !customer.getCustPassword().isBlank()) {
            existing.setCustPassword(encodePassword(customer.getCustPassword()));
        }
        existing.setCustPhoneno(customer.getCustPhoneno());
        existing.setCustType(customer.getCustType());
        existing.setCustStatus(customer.getCustStatus());

        return customerRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponse getCustomerProfile(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customer account not found."));
        return CustomerProfileResponse.from(customer);
    }

    @Transactional
    public CustomerProfileResponse updateCustomerProfile(
            Long customerId,
            CustomerProfileUpdateRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customer account not found."));
        String email = request.email().trim();
        customerRepository.findByCustEmailIgnoreCase(email)
                .filter(existing -> !existing.getCustId().equals(customerId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "That email address is already used by another account.");
                });

        customer.setCustName(request.name().trim());
        customer.setCustEmail(email);
        customer.setCustPhoneno(
                request.phone() == null || request.phone().isBlank()
                        ? null
                        : request.phone().trim());
        boolean hasCompanyFields = request.companyName() != null
                || request.companyRegistrationNumber() != null;
        if (hasCompanyFields && !"COMPANY".equalsIgnoreCase(customer.getCustType())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Corporate registration details are only available to company customers.");
        }
        if (request.companyName() != null) {
            customer.setCompanyName(request.companyName().isBlank()
                    ? null : request.companyName().trim());
        }
        if (request.companyRegistrationNumber() != null) {
            customer.setCompanyRegistrationNumber(
                    request.companyRegistrationNumber().isBlank()
                            ? null : request.companyRegistrationNumber().trim());
        }
        if (request.profileImage() != null) {
            if (!request.profileImage().isEmpty()
                    && !request.profileImage().matches("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Profile image must be a PNG, JPEG, or WebP image.");
            }
            customer.setProfileImage(request.profileImage().isEmpty()
                    ? null : request.profileImage());
        }
        return CustomerProfileResponse.from(customerRepository.save(customer));
    }

    public void deleteCustomer(Long id) {
        customerRepository.deleteById(id);
    }

    private String encodePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("A password is required.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("The password must not exceed 72 UTF-8 bytes.");
        }
        return passwordEncoder.encode(password);
    }
}
