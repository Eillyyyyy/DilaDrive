package com.diladrive.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.diladrive.common.ApiException;
import com.diladrive.security.JwtService;
import com.diladrive.user.AccountStatus;
import com.diladrive.user.Customer;
import com.diladrive.user.CustomerRepository;
import com.diladrive.user.CustomerType;
import com.diladrive.user.Role;
import com.diladrive.user.User;
import com.diladrive.user.UserRepository;
import com.diladrive.user.VerificationStatus;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email/username or password";

    private final UserRepository users;
    private final CustomerRepository customers;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, CustomerRepository customers, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.customers = customers;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** Public self-registration. Always creates a CUSTOMER in PENDING state; admin verifies later. */
    @Transactional
    public UserResponse registerCustomer(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("Email is already registered");
        }
        if (users.existsByUsernameIgnoreCase(req.username())) {
            throw ApiException.conflict("Username is already taken");
        }
        if (req.customerType() == CustomerType.CORPORATE
                && (req.companyName() == null || req.companyName().isBlank())) {
            throw ApiException.badRequest("Company name is required for corporate customers");
        }

        User user = new User();
        user.setFullName(req.fullName().trim());
        user.setPhoneNo(req.phoneNo().trim());
        user.setEmail(req.email().trim().toLowerCase());
        user.setUsername(req.username().trim());
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRole(Role.CUSTOMER);
        user.setAccountStatus(AccountStatus.PENDING);
        user = users.save(user);

        Customer customer = new Customer();
        customer.setUser(user);
        customer.setCustomerType(req.customerType());
        customer.setVerificationStatus(VerificationStatus.UNVERIFIED);
        if (req.customerType() == CustomerType.CORPORATE) {
            customer.setCompanyName(req.companyName());
            customer.setSupplierNo(req.supplierNo());
            customer.setContactPerson(req.contactPerson());
        }
        customer.setBillingAddress(req.billingAddress());
        customer = customers.save(customer);

        return UserResponse.of(user, customer);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCaseOrUsernameIgnoreCase(req.identifier().trim(), req.identifier().trim())
                .orElseThrow(() -> ApiException.unauthorized(INVALID_CREDENTIALS));

        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        if (user.getAccountStatus() == AccountStatus.SUSPENDED) {
            throw ApiException.forbidden("This account has been suspended. Please contact the administrator.");
        }
        // Decision D2: admin = web only; customer/driver = mobile app only.
        if (req.client() == ClientType.MOBILE && user.getRole() == Role.ADMIN) {
            throw ApiException.forbidden("Admin accounts must sign in through the web dashboard");
        }
        if (req.client() == ClientType.WEB && user.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("The web dashboard is for administrators only. Please use the mobile app");
        }

        return new AuthResponse(jwt.issue(user), "Bearer", jwt.lifetimeSeconds(), toResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        Customer customer = user.getRole() == Role.CUSTOMER
                ? customers.findByUserId(user.getId()).orElse(null)
                : null;
        return UserResponse.of(user, customer);
    }
}
