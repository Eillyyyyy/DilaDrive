package com.diladrive.dto;

import com.diladrive.entity.Customer;

public record CustomerProfileResponse(
        Long id,
        String name,
        String email,
        String phone,
        String status,
        String type,
        String companyName,
        String companyRegistrationNumber,
        String profileImage) {

    public static CustomerProfileResponse from(Customer customer) {
        return new CustomerProfileResponse(
                customer.getCustId(),
                customer.getCustName(),
                customer.getCustEmail(),
                customer.getCustPhoneno(),
                customer.getCustStatus(),
                customer.getCustType(),
                customer.getCompanyName(),
                customer.getCompanyRegistrationNumber(),
                customer.getProfileImage());
    }
}
