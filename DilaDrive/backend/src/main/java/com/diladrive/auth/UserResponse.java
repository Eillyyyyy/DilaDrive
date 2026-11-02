package com.diladrive.auth;

import com.diladrive.user.AccountStatus;
import com.diladrive.user.Customer;
import com.diladrive.user.CustomerType;
import com.diladrive.user.Role;
import com.diladrive.user.User;
import com.diladrive.user.VerificationStatus;

public record UserResponse(
        Long userId,
        String fullName,
        String phoneNo,
        String email,
        String username,
        Role role,
        AccountStatus accountStatus,
        Long customerId,
        CustomerType customerType,
        VerificationStatus verificationStatus) {

    public static UserResponse of(User u, Customer c) {
        return new UserResponse(
                u.getId(), u.getFullName(), u.getPhoneNo(), u.getEmail(), u.getUsername(),
                u.getRole(), u.getAccountStatus(),
                c == null ? null : c.getId(),
                c == null ? null : c.getCustomerType(),
                c == null ? null : c.getVerificationStatus());
    }
}
