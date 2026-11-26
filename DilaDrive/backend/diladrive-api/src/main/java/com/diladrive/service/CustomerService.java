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

    @Transactional
    public Customer updateCustomer(Long id, Customer customer) {
        if (customer.getCustStatus() != null) {
            throw new IllegalArgumentException("Use account verification to change customer status.");
        }

        Customer existing =
                customerRepository.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer account not found."));

        if (customer.getCompanyName() != null || customer.getCompanyRegistrationNumber() != null) {
            String type = customer.getCustType() == null ? existing.getCustType() : customer.getCustType();
            if (!"COMPANY".equalsIgnoreCase(type)) {
                throw new IllegalArgumentException("Company details require a corporate account.");
            }
            applyCompanyDetails(existing, customer);
        }

        existing.setCustName(customer.getCustName());
        existing.setCustEmail(customer.getCustEmail());
        if (customer.getCustPassword() != null
                && !customer.getCustPassword().isBlank()) {
            existing.setCustPassword(encodePassword(customer.getCustPassword()));
        }
        existing.setCustPhoneno(customer.getCustPhoneno());
        if (customer.getCustType() != null) {
            existing.setCustType(customer.getCustType());
        }
        return customerRepository.save(existing);
    }

    private void applyCompanyDetails(Customer existing, Customer requested) {
        String name = requested.getCompanyName() == null ? existing.getCompanyName() : requested.getCompanyName();
        String registration = requested.getCompanyRegistrationNumber() == null
                ? existing.getCompanyRegistrationNumber() : requested.getCompanyRegistrationNumber();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name is required for a corporate account.");
        }
        if (name.trim().length() > 150 || (registration != null && registration.trim().length() > 30)) {
            throw new IllegalArgumentException("Company name and registration number must be at most 150 and 30 characters.");
        }
        existing.setCompanyName(name.trim());
        if (requested.getCompanyRegistrationNumber() != null) {
            existing.setCompanyRegistrationNumber(registration.isBlank() ? null : registration.trim());
        }
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

        boolean hasCompanyFields = request.companyName() != null
                || request.companyRegistrationNumber() != null;
        boolean corporate = "COMPANY".equalsIgnoreCase(customer.getCustType());
        if (hasCompanyFields && !corporate) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Corporate registration details are only available to company customers.");
        }
        String companyName = request.companyName() == null ? customer.getCompanyName() : request.companyName();
        String companyNumber = request.companyRegistrationNumber() == null
                ? customer.getCompanyRegistrationNumber() : request.companyRegistrationNumber();
        if (corporate && (companyName == null || companyName.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Company name is required for a corporate account.");
        }
        String phone = request.phone() == null || request.phone().isBlank()
                ? null : ProfileUpdateValidation.phone(request.phone());
        String image = request.profileImage() == null ? null
                : ProfileUpdateValidation.customerImage(request.profileImage());

        customer.setCustName(request.name().trim());
        customer.setCustEmail(email);
        if (request.clearPhone()) customer.setCustPhoneno(null);
        else if (phone != null) customer.setCustPhoneno(phone);
        if (request.companyName() != null) {
            customer.setCompanyName(companyName.trim());
        }
        if (request.companyRegistrationNumber() != null) {
            customer.setCompanyRegistrationNumber(companyNumber.isBlank() ? null : companyNumber.trim());
        }
        if (request.profileImage() != null) {
            customer.setProfileImage(image);
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
