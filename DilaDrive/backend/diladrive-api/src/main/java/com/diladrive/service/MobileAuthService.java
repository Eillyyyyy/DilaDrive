package com.diladrive.service;

import com.diladrive.dto.NotificationTarget;

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
    @org.springframework.beans.factory.annotation.Autowired private NotificationService notifications;

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
                || request.getEmail().length() > 254
                || request.getPassword() == null
                || request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            return Optional.empty();
        }

        String email = request.getEmail();
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
        String email = request.email();
        String type = request.type();
        if (!java.util.Objects.equals(request.password(), request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        }
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
        String address = trimToNull(request.companyAddress());
        String contactPerson = trimToNull(request.contactPerson());
        String name = trimToNull(request.name());
        if (corporate && (companyName == null || address == null || contactPerson == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Company name, address and contact person are required.");
        }
        if (!corporate && name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name is required.");
        }

        Customer customer = new Customer();
        customer.setCustType(type);
        customer.setCustName(corporate ? companyName : name);
        customer.setCompanyAddress(corporate ? address : null);
        customer.setContactPerson(corporate ? contactPerson : null);
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
        if (notifications != null) {
            notifications.notifyAdmins("Customer registration awaiting review",
                    "A new customer account is awaiting verification.", "CUSTOMER_REGISTRATION",
                    NotificationTarget.CUSTOMER, customer.getCustId(), "customer-registered:" + customer.getCustId());
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
        if (!"ACTIVE".equalsIgnoreCase(driver.getDriverStatus())) {
            return false;
        }
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
