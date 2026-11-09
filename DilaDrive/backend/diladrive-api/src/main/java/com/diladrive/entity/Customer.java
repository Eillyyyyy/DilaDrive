package com.diladrive.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@Table(name = "customer")
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

    public String getCustPassword() {
        return custPassword;
    }

    public void setCustPassword(String custPassword) {
        this.custPassword = custPassword;
    }
}