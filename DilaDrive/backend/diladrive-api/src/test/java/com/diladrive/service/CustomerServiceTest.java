package com.diladrive.service;

import com.diladrive.entity.Customer;
import com.diladrive.repository.CustomerRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerServiceTest {
    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final CustomerService service = new CustomerService(repository);

    @Test
    void adminCanUpdateRegisteredCorporateDetailsWithoutAContract() {
        Customer customer = new Customer();
        customer.setCustType("COMPANY");
        customer.setCompanyName("Example Transport");
        customer.setCompanyRegistrationNumber("REG123");
        when(repository.save(any(Customer.class))).thenAnswer(call -> call.getArgument(0));
        when(repository.findById(1L)).thenReturn(Optional.of(customer));

        Customer update = new Customer();
        update.setCompanyName(" Updated Company ");
        var updated = service.updateCustomer(1L, update);
        assertEquals("Updated Company", updated.getCompanyName());
        assertEquals("REG123", updated.getCompanyRegistrationNumber());
    }

    @Test
    void incompleteCorporateUpdatesCannotBeSaved() {
        Customer customer = new Customer();
        customer.setCustType("COMPANY");
        customer.setCompanyName("Example Transport");
        customer.setCompanyRegistrationNumber("REG123");
        when(repository.findById(1L)).thenReturn(Optional.of(customer));

        Customer update = new Customer();
        update.setCompanyName("  ");
        assertThrows(IllegalArgumentException.class, () -> service.updateCustomer(1L, update));
        verify(repository, never()).save(any(Customer.class));
    }
}
