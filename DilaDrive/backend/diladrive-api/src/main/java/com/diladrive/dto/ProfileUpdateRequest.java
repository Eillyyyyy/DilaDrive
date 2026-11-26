package com.diladrive.dto;

public class ProfileUpdateRequest {

    @jakarta.validation.constraints.Size(max = 120)
    private String name;
    @jakarta.validation.constraints.Size(max = 254)
    private String email;
    private String phone;
    private String address;
    private String profileImage;
    private boolean clearPhone;
    private boolean clearAddress;

    public boolean isClearPhone() {
        return clearPhone;
    }

    public void setClearPhone(boolean clearPhone) {
        this.clearPhone = clearPhone;
    }

    public boolean isClearAddress() {
        return clearAddress;
    }

    public void setClearAddress(boolean clearAddress) {
        this.clearAddress = clearAddress;
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
}
