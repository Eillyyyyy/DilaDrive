package com.diladrive.dto;

import com.diladrive.entity.Customer;
import jakarta.validation.constraints.*;

public record AdminCustomerUpdateRequest(
        @NotBlank @Size(max = 120) String custName,
        @NotBlank @Email @Size(max = 160) String custEmail,
        @Size(max = 30) String custPhoneno,
        @Size(max = 150) String companyName,
        @Size(max = 30) String companyRegistrationNumber) {

    public Customer toUpdate() {
        var customer = new Customer();
        customer.setCustName(custName.strip());
        customer.setCustEmail(custEmail.strip().toLowerCase(java.util.Locale.ROOT));
        customer.setCustPhoneno(custPhoneno);
        customer.setCompanyName(companyName);
        customer.setCompanyRegistrationNumber(companyRegistrationNumber);
        return customer;
    }
}
