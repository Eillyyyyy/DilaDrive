package com.diladrive.dto;

public class MobileLoginResponse {

    private final Long id;
    private final String name;
    private final String email;
    private final String phone;
    private final String status;
    private final String role;

    public MobileLoginResponse(
            Long id,
            String name,
            String email,
            String phone,
            String status,
            String role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.status = status;
        this.role = role;
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

    public String getStatus() {
        return status;
    }

    public String getRole() {
        return role;
    }
}
