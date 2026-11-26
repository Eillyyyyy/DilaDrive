
package com.diladrive.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer(
            @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:http://localhost:5173}") String origins) {
        String[] allowedOrigins = java.util.Arrays.stream(origins.split(","))
                .map(String::strip).filter(origin -> !origin.isEmpty()).toArray(String[]::new);
        if (java.util.Arrays.stream(allowedOrigins).anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("Credentialed CORS requires explicit origins.");
        }
        return new WebMvcConfigurer() {

            @Override
            public void addCorsMappings(CorsRegistry registry) {

                registry.addMapping("/api/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods(
                                "GET",
                                "POST",
                                "PUT",
                                "PATCH",
                                "DELETE",
                                "OPTIONS"
                        )
                        .allowedHeaders("Content-Type", "Authorization", "Accept", "Idempotency-Key", "X-CSRF-TOKEN", "X-XSRF-TOKEN")
                        .exposedHeaders("X-Next-Page")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
