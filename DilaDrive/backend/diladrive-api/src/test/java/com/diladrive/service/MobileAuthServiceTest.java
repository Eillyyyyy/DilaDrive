package com.diladrive.service;

import com.diladrive.dto.MobileLoginRequest;
import com.diladrive.dto.MobileLoginResponse;
import com.diladrive.dto.MobileRegistrationRequest;
import com.diladrive.entity.Customer;
import com.diladrive.entity.Driver;
import com.diladrive.repository.CustomerRepository;
import com.diladrive.repository.DriverRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobileAuthServiceTest {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final DriverRepository driverRepository = mock(DriverRepository.class);
    private final MobileAuthService service =
            new MobileAuthService(customerRepository, driverRepository, new PasswordService());

    @Test
    void customerLoginMigratesLegacyPasswordAndReturnsSafeProfile() throws Exception {
        Customer customer = new Customer();
        customer.setCustId(7L);
        customer.setCustName("Test Customer");
        customer.setCustEmail("customer@example.com");
        customer.setCustPassword("plain-password");
        when(customerRepository.findByCustEmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.of(customer));

        Optional<MobileLoginResponse> result = service.login(
                request("customer@example.com", "plain-password", "CUSTOMER"));

        assertTrue(result.isPresent());
        assertEquals(7L, result.get().getId());
        assertEquals("CUSTOMER", result.get().getRole());
        assertTrue(new BCryptPasswordEncoder().matches(
                "plain-password", customer.getCustPassword()));
        verify(customerRepository).save(customer);
        assertFalse(new ObjectMapper().writeValueAsString(customer).contains("plain-password"));
    }

    @Test
    void driverLoginAcceptsBcryptPassword() {
        Driver driver = new Driver();
        driver.setDriverId(9L);
        driver.setDriverName("Test Driver");
        driver.setDriverEmail("driver@example.com");
        driver.setDriverPassword(new BCryptPasswordEncoder().encode("driver-password"));
        when(driverRepository.findByDriverEmailIgnoreCase("driver@example.com"))
                .thenReturn(Optional.of(driver));

        Optional<MobileLoginResponse> result = service.login(
                request("driver@example.com", "driver-password", "DRIVER"));

        assertTrue(result.isPresent());
        assertEquals("DRIVER", result.get().getRole());
        verify(driverRepository, never()).save(any());
    }

    @Test
    void rejectsWrongPasswordAndUnknownRole() {
        Customer customer = new Customer();
        customer.setCustPassword("correct-password");
        when(customerRepository.findByCustEmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.of(customer));

        assertTrue(service.login(
                request("customer@example.com", "wrong-password", "CUSTOMER")).isEmpty());
        assertTrue(service.login(
                request("customer@example.com", "correct-password", "ADMIN")).isEmpty());
        verify(customerRepository, never()).save(any());
    }

    @Test
    void customerRegistrationStoresHashedPasswordAndPendingCompanyDetails() {
        when(customerRepository.findByCustEmailIgnoreCase("owner@example.com"))
                .thenReturn(Optional.empty());

        service.registerCustomer(new MobileRegistrationRequest(
                "  Company Owner ", " owner@example.com ", " 0123 ", "company",
                " Example Sdn Bhd ", " 123456-A ", "secure-pass-123"));

        var captor = org.mockito.ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).saveAndFlush(captor.capture());
        Customer saved = captor.getValue();
        assertEquals("Company Owner", saved.getCustName());
        assertEquals("owner@example.com", saved.getCustEmail());
        assertEquals("COMPANY", saved.getCustType());
        assertEquals("PENDING", saved.getCustStatus());
        assertEquals("Example Sdn Bhd", saved.getCompanyName());
        assertEquals("123456-A", saved.getCompanyRegistrationNumber());
        assertTrue(new BCryptPasswordEncoder().matches("secure-pass-123", saved.getCustPassword()));
    }

    @Test
    void rejectsDuplicateRegistrationEmail() {
        when(customerRepository.findByCustEmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.of(new Customer()));

        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.registerCustomer(new MobileRegistrationRequest(
                        "Customer", "customer@example.com", null, "NORMAL", null, null,
                        "secure-pass-123")));

        assertEquals(409, exception.getStatusCode().value());
        verify(customerRepository, never()).saveAndFlush(any());
    }

    @Test
    void corporateRegistrationRequiresCompanyDetails() {
        when(customerRepository.findByCustEmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.registerCustomer(new MobileRegistrationRequest(
                        "Customer", "customer@example.com", null, "COMPANY", null, null,
                        "secure-pass-123")));
        verify(customerRepository, never()).saveAndFlush(any());
    }

    private MobileLoginRequest request(String email, String password, String role) {
        MobileLoginRequest request = new MobileLoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setRole(role);
        return request;
    }
}
