package com.diladrive.service;

import java.util.Objects;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        return isBcryptHash(storedPassword)
                ? passwordEncoder.matches(rawPassword, storedPassword)
                : Objects.equals(storedPassword, rawPassword);
    }

    public boolean isEncoded(String storedPassword) {
        return isBcryptHash(storedPassword);
    }

    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    private boolean isBcryptHash(String value) {
        return value != null
                && value.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    }
}
