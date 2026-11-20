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
    void adminCanCreateAndUpdateCorporateDetailsWithoutAContract() {
        Customer customer = new Customer();
        customer.setCustType("COMPANY");
        customer.setCustPassword("test-password");
        customer.setCompanyName(" Example Transport ");
        customer.setCompanyRegistrationNumber(" REG123 ");
        when(repository.save(any(Customer.class))).thenAnswer(call -> call.getArgument(0));
        when(repository.findById(1L)).thenReturn(Optional.of(customer));

        var created = service.createCustomer(customer);
        assertEquals("Example Transport", created.getCompanyName());
        assertEquals("REG123", created.getCompanyRegistrationNumber());
        assertEquals("PENDING", created.getCustStatus());
        assertNotEquals("test-password", created.getCustPassword());

        Customer update = new Customer();
        update.setCompanyName(" Updated Company ");
        var updated = service.updateCustomer(1L, update);
        assertEquals("Updated Company", updated.getCompanyName());
        assertEquals("REG123", updated.getCompanyRegistrationNumber());
    }

    @Test
    void incompleteCorporateDetailsCannotBeSaved() {
        Customer customer = new Customer();
        customer.setCustType("COMPANY");
        customer.setCompanyName("  ");
        customer.setCompanyRegistrationNumber("REG123");

        assertThrows(IllegalArgumentException.class, () -> service.createCustomer(customer));
        verifyNoInteractions(repository);
    }
}
