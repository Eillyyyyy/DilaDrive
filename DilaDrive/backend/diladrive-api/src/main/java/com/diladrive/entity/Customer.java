package com.diladrive.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "customer")
@DynamicUpdate
public class Customer {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "customer_seq"
    )
    @SequenceGenerator(
            name = "customer_seq",
            sequenceName = "customer_cust_id_seq",
            allocationSize = 1
    )
    @Column(name = "cust_id")
    private Long custId;

    @Column(name = "cust_type")
    private String custType;

    @Column(name = "cust_name")
    private String custName;

    @Column(name = "cust_email")
    private String custEmail;

    @Column(name = "cust_phoneno")
    private String custPhoneno;

    @Column(name = "cust_status")
    private String custStatus;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by_admin_id")
    private Long verifiedByAdminId;

    @JsonIgnore
    public String getRejectionReason() {
        return rejectionReason;
    }

    public void recordVerification(String status, String reason, Instant timestamp, Long reviewerId) {
        custStatus = status;
        rejectionReason = reason;
        verifiedAt = timestamp;
        verifiedByAdminId = reviewerId;
    }

    @Column(name = "cust_companyname", length = 150)
    private String companyName;

    @Column(name = "cust_companyaddress", columnDefinition = "text")
    private String companyAddress;

    @Column(name = "cust_contactperson", length = 100)
    private String contactPerson;

    @Column(name = "company_registration_number", length = 30)
    private String companyRegistrationNumber;

    @Column(name = "cust_profile_image", columnDefinition = "TEXT")
    private String profileImage;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "cust_password")
    private String custPassword;

    public Customer() {
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
    }

    public String getCustType() {
        return custType;
    }

    public void setCustType(String custType) {
        this.custType = custType;
    }

    public String getCustName() {
        return custName;
    }

    public void setCustName(String custName) {
        this.custName = custName;
    }

    public String getCustEmail() {
        return custEmail;
    }

    public void setCustEmail(String custEmail) {
        this.custEmail = custEmail;
    }

    public String getCustPhoneno() {
        return custPhoneno;
    }

    public void setCustPhoneno(String custPhoneno) {
        this.custPhoneno = custPhoneno;
    }

    public String getCustStatus() {
        return custStatus;
    }

    public void setCustStatus(String custStatus) {
        this.custStatus = custStatus;
    }

    public String getCompanyAddress() {
        return companyAddress;
    }

    public void setCompanyAddress(String companyAddress) {
        this.companyAddress = companyAddress;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCompanyRegistrationNumber() {
        return companyRegistrationNumber;
    }

    public String getProfileImage() { return profileImage; }

    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public void setCompanyRegistrationNumber(String companyRegistrationNumber) {
        this.companyRegistrationNumber = companyRegistrationNumber;
    }

    public String getCustPassword() {
        return custPassword;
    }

    public void setCustPassword(String custPassword) {
        this.custPassword = custPassword;
    }
}
