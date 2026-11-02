package com.diladrive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin-seed")
public record AdminSeedProperties(String email, String username, String password, String fullName, String phone) {
}
