package com.diladrive.dto;

public class ChangePasswordRequest {

    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 72)
    private String currentPassword;
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 8, max = 72)
    private String newPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
