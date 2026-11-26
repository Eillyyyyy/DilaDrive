package com.diladrive.config;

import com.diladrive.repository.DriverRepository;
import com.diladrive.security.DriverSessionGuard;
import com.diladrive.security.ApiSessionAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.handler.MappedInterceptor;

@Configuration
public class DriverSessionConfig {
    @Bean
    MappedInterceptor apiSessionAccessInterceptor(com.diladrive.repository.AdminRepository admins) {
        return new MappedInterceptor(new String[]{"/api/**"}, new ApiSessionAccess(admins));
    }

    @Bean
    MappedInterceptor driverSessionInterceptor(DriverRepository drivers, ObjectMapper json) {
        return new MappedInterceptor(
                new String[]{"/api/**"},
                new String[]{"/api/auth/login", "/api/auth/logout",
                        "/api/auth/mobile/login", "/api/auth/mobile/register"},
                new DriverSessionGuard(drivers, json));
    }
}
