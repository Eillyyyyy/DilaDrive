package com.diladrive.service;

import com.diladrive.entity.Customer;
import com.diladrive.repository.CustomerRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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