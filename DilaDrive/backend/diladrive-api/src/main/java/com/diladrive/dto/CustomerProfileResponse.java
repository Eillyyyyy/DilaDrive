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
        String profileImage,
        String rejectionReason) {

    public CustomerProfileResponse(Long id, String name, String email, String phone, String status,
            String type, String companyName, String companyRegistrationNumber, String profileImage) {
        this(id, name, email, phone, status, type, companyName, companyRegistrationNumber, profileImage, null);
    }

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
                customer.getProfileImage(),
                "REJECTED".equalsIgnoreCase(customer.getCustStatus()) ? customer.getRejectionReason() : null);
    }
}
