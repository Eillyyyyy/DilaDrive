package com.diladrive.service;

import com.diladrive.dto.MobileLoginRequest;
import com.diladrive.dto.MobileLoginResponse;
import com.diladrive.entity.Customer;
import com.diladrive.entity.Driver;
import com.diladrive.repository.CustomerRepository;
import com.diladrive.repository.DriverRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

@Service
public class MobileAuthService {

    private final CustomerRepository customerRepository;
    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MobileAuthService(
            CustomerRepository customerRepository,
            DriverRepository driverRepository) {
        this.customerRepository = customerRepository;
        this.driverRepository = driverRepository;
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

    private boolean authenticateCustomer(Customer customer, String password) {
        String storedPassword = customer.getCustPassword();
        if (isPasswordHash(storedPassword)) {
            return passwordEncoder.matches(password, storedPassword);
        }
        if (!Objects.equals(storedPassword, password)) {
            return false;
        }

        customer.setCustPassword(passwordEncoder.encode(password));
        customerRepository.save(customer);
        return true;
    }

    private boolean authenticateDriver(Driver driver, String password) {
        String storedPassword = driver.getDriverPassword();
        if (isPasswordHash(storedPassword)) {
            return passwordEncoder.matches(password, storedPassword);
        }
        if (!Objects.equals(storedPassword, password)) {
            return false;
        }

        driver.setDriverPassword(passwordEncoder.encode(password));
        driverRepository.save(driver);
        return true;
    }

    private boolean isPasswordHash(String value) {
        return value != null
                && value.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    }
}
