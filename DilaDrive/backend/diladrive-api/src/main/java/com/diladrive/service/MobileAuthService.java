package com.diladrive.service;

import com.diladrive.dto.MobileLoginRequest;
import com.diladrive.dto.MobileLoginResponse;
import com.diladrive.dto.MobileRegistrationRequest;
import com.diladrive.entity.Customer;
import com.diladrive.entity.Driver;
import com.diladrive.repository.CustomerRepository;
import com.diladrive.repository.DriverRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class MobileAuthService {

    private final CustomerRepository customerRepository;
    private final DriverRepository driverRepository;
    private final PasswordService passwordService;

    public MobileAuthService(
            CustomerRepository customerRepository,
            DriverRepository driverRepository,
            PasswordService passwordService) {
        this.customerRepository = customerRepository;
        this.driverRepository = driverRepository;
        this.passwordService = passwordService;
    }

    public Optional<MobileLoginResponse> login(MobileLoginRequest request) {
        if (request == null
                || request.getEmail() == null
                || request.getPassword() == null
                || request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            return Optional.empty();
        }

        String email = request.getEmail().trim();
        String password = request.getPassword();
        if (email.isBlank() || password.isEmpty()) {
            return Optional.empty();
        }

        if ("CUSTOMER".equalsIgnoreCase(request.getRole())) {
            return customerRepository.findByCustEmailIgnoreCase(email)
                    .filter(customer -> authenticateCustomer(customer, password))
                    .map(customer -> new MobileLoginResponse(
                            customer.getCustId(),
                            customer.getCustName(),
                            customer.getCustEmail(),
                            customer.getCustPhoneno(),
                            customer.getCustStatus(),
                            "CUSTOMER"));
        }

        if ("DRIVER".equalsIgnoreCase(request.getRole())) {
            return driverRepository.findByDriverEmailIgnoreCase(email)
                    .filter(driver -> authenticateDriver(driver, password))
                    .map(driver -> new MobileLoginResponse(
                            driver.getDriverId(),
                            driver.getDriverName(),
                            driver.getDriverEmail(),
                            driver.getDriverPhoneno(),
                            driver.getDriverStatus(),
                            "DRIVER"));
        }

        return Optional.empty();
    }

    @Transactional
    public void registerCustomer(MobileRegistrationRequest request) {
        String email = request.email().trim();
        String type = request.type().trim().toUpperCase();
        if (passwordTooLong(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The password must not exceed 72 UTF-8 bytes.");
        }
        if (customerRepository.findByCustEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That email address is already registered.");
        }
        boolean corporate = "COMPANY".equals(type);
        String companyName = trimToNull(request.companyName());
        String registrationNumber = trimToNull(request.companyRegistrationNumber());
        if (corporate && (companyName == null || registrationNumber == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Company name and registration number are required for a corporate account.");
        }

        Customer customer = new Customer();
        customer.setCustType(type);
        customer.setCustName(request.name().trim());
        customer.setCustEmail(email);
        customer.setCustPhoneno(trimToNull(request.phone()));
        customer.setCustStatus("PENDING");
        customer.setCustPassword(passwordService.encode(request.password()));
        customer.setCompanyName(corporate ? companyName : null);
        customer.setCompanyRegistrationNumber(corporate ? registrationNumber : null);
        try {
            customerRepository.saveAndFlush(customer);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That email address is already registered.");
        }
    }

    private static boolean passwordTooLong(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > 72;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean authenticateCustomer(Customer customer, String password) {
        String storedPassword = customer.getCustPassword();
        if (!passwordService.matches(password, storedPassword)) {
            return false;
        }

        if (!passwordService.isEncoded(storedPassword)) {
            customer.setCustPassword(passwordService.encode(password));
            customerRepository.save(customer);
        }
        return true;
    }

    private boolean authenticateDriver(Driver driver, String password) {
        String storedPassword = driver.getDriverPassword();
        if (!passwordService.matches(password, storedPassword)) {
            return false;
        }

        if (!passwordService.isEncoded(storedPassword)) {
            driver.setDriverPassword(passwordService.encode(password));
            driverRepository.save(driver);
        }
        return true;
    }
}
