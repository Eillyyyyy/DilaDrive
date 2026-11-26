package com.diladrive.dto;

public class LoginRequest {

    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 254)
    private String email;
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 72)
    private String password;

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
}
