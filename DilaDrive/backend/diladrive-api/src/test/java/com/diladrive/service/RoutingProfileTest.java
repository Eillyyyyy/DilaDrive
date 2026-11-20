package com.diladrive.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import static org.junit.jupiter.api.Assertions.*;

class RoutingProfileTest {
    @org.springframework.boot.test.context.TestConfiguration static class Config {}

    @Test void normalLaunchHasDevelopmentRouting() {
        try (var context = new SpringApplicationBuilder(Config.class).web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off")) {
            assertArrayEquals(new String[]{"development"}, context.getEnvironment().getDefaultProfiles());
            assertEquals("https://router.project-osrm.org", context.getEnvironment().getProperty("booking.routing-url"));
        }
    }

    @Test void productionDoesNotInheritPublicDemo() {
        try (var context = new SpringApplicationBuilder(Config.class).web(WebApplicationType.NONE)
                .run("--spring.profiles.active=production", "--spring.main.banner-mode=off")) {
            assertEquals("", context.getEnvironment().getProperty("booking.routing-url"));
        }
    }
}
