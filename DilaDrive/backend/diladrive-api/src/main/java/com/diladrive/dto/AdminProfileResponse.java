package com.diladrive.dto;

import com.diladrive.entity.Admin;

public class AdminProfileResponse {

    private final Long id;
    private final String name;
    private final String email;
    private final String phone;
    private final String address;
    private final String profileImage;
    private final String role;

    public AdminProfileResponse(Admin admin) {
        this.id = admin.getId();
        this.name = admin.getName();
        this.email = admin.getEmail();
        this.phone = admin.getPhone();
        this.address = admin.getAddress();
        this.profileImage = admin.getProfileImage();
        this.role = admin.getRole() == null ? "ADMIN" : admin.getRole();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public String getRole() {
        return role;
    }
}
