package com.diladrive.controller;

import com.diladrive.entity.Customer;
import com.diladrive.repository.CustomerRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerRepository repository;

    public CustomerController(CustomerRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Customer> getAllCustomers() {
        return repository.findAll();
    }

    @PostMapping
    public Customer createCustomer(
            @RequestBody Customer customer) {

        return repository.save(customer);
    }
}