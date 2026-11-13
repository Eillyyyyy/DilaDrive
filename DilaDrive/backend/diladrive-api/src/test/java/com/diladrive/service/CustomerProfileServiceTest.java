package com.diladrive.service;

import com.diladrive.dto.CustomerProfileUpdateRequest;
import com.diladrive.entity.Customer;
import com.diladrive.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CustomerProfileServiceTest {

    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final CustomerService service = new CustomerService(repository);

    @Test
    void profileUpdatePreservesCustomerTypeStatusAndPassword() {
        Customer customer = customer(42L);
        when(repository.findById(42L)).thenReturn(Optional.of(customer));
        when(repository.findByCustEmailIgnoreCase("new@example.com"))
                .thenReturn(Optional.empty());
        when(repository.save(customer)).thenReturn(customer);

        var result = service.updateCustomerProfile(
                42L,
                new CustomerProfileUpdateRequest(
                        " Updated Name ", " new@example.com ", " 0123456789 ", null, null, null));

        assertEquals("Updated Name", result.name());
        assertEquals("NORMAL", result.type());
        assertEquals("ACTIVE", result.status());
        assertEquals("existing-hash", customer.getCustPassword());
        assertEquals("0123456789", customer.getCustPhoneno());
    }

    @Test
    void corporateProfileCanUpdateItsStoredRegistrationDetails() {
        Customer customer = customer(42L);
        customer.setCustType("COMPANY");
        customer.setCompanyName("Old Company");
        customer.setCompanyRegistrationNumber("OLD-123");
        when(repository.findById(42L)).thenReturn(Optional.of(customer));
        when(repository.findByCustEmailIgnoreCase("original@example.com"))
                .thenReturn(Optional.of(customer));
        when(repository.save(customer)).thenReturn(customer);

        var result = service.updateCustomerProfile(
                42L,
                new CustomerProfileUpdateRequest(
                        "Original Name", "original@example.com", null,
                        " New Company ", " NEW-456 ", null));

        assertEquals("New Company", customer.getCompanyName());
        assertEquals("NEW-456", customer.getCompanyRegistrationNumber());
        assertEquals("New Company", result.companyName());
        assertEquals("NEW-456", result.companyRegistrationNumber());
    }

    @Test
    void omittedCorporateFieldsArePreservedForExistingClients() {
        Customer customer = customer(42L);
        customer.setCustType("COMPANY");
        customer.setCompanyName("Existing Company");
        customer.setCompanyRegistrationNumber("REG-123");
        when(repository.findById(42L)).thenReturn(Optional.of(customer));
        when(repository.findByCustEmailIgnoreCase("original@example.com"))
                .thenReturn(Optional.of(customer));
        when(repository.save(customer)).thenReturn(customer);

        service.updateCustomerProfile(
                42L,
                new CustomerProfileUpdateRequest(
                        "Original Name", "original@example.com", null, null, null, null));

        assertEquals("Existing Company", customer.getCompanyName());
        assertEquals("REG-123", customer.getCompanyRegistrationNumber());
    }

    @Test
    void normalCustomerCannotWriteCorporateRegistrationDetails() {
        Customer customer = customer(42L);
        when(repository.findById(42L)).thenReturn(Optional.of(customer));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.updateCustomerProfile(
                        42L,
                        new CustomerProfileUpdateRequest(
                                "Original Name", "original@example.com", null,
                                "Unexpected Company", null, null)));

        assertEquals(403, error.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void profileUpdateRejectsEmailOwnedByAnotherCustomer() {
        Customer customer = customer(42L);
        Customer otherCustomer = customer(99L);
        otherCustomer.setCustEmail("taken@example.com");
        when(repository.findById(42L)).thenReturn(Optional.of(customer));
        when(repository.findByCustEmailIgnoreCase("taken@example.com"))
                .thenReturn(Optional.of(otherCustomer));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.updateCustomerProfile(
                        42L,
                        new CustomerProfileUpdateRequest(
                                "Updated Name", "taken@example.com", null, null, null, null)));

        assertEquals(409, error.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    private Customer customer(Long id) {
        Customer customer = new Customer();
        customer.setCustId(id);
        customer.setCustName("Original Name");
        customer.setCustEmail("original@example.com");
        customer.setCustType("NORMAL");
        customer.setCustStatus("ACTIVE");
        customer.setCustPassword("existing-hash");
        return customer;
    }
}
