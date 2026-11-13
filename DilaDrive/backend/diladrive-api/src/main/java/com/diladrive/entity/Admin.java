package com.diladrive.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "admin")
public class Admin {

    @Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "admin_id")
private Long id;

    @Column(name = "admin_name")
    private String name;

    @Column(name = "admin_email")
    private String email;

    @Column(name = "admin_password")
    private String password;

    @Column(name = "admin_phone", length = 30)
    private String phone;

    @Column(name = "admin_address", length = 500)
    private String address;

    @Column(name = "admin_profile_image", columnDefinition = "TEXT")
    private String profileImage;

    @Column(name = "admin_role", nullable = false, length = 20)
    private String role = "ADMIN";

    @Column(name = "admin_status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "admin_createdat", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "admin_updatedat", nullable = false)
    private Instant updatedAt;

    public Admin() {}

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (role == null || role.isBlank()) {
            role = "ADMIN";
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}