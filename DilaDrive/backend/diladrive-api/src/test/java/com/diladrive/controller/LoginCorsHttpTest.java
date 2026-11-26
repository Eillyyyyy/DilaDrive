package com.diladrive.controller;

import com.diladrive.config.CorsConfig;
import com.diladrive.dto.MobileLoginResponse;
import com.diladrive.service.AdminService;
import com.diladrive.service.MobileAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Real HTTP server, real MVC/CORS/controller; no datasource or database writes.
@SpringBootTest(classes = LoginCorsHttpTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginCorsHttpTest {
    @Configuration
    @org.springframework.boot.test.context.TestComponent
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @Import({AuthController.class, CorsConfig.class})
    static class Application {}

    @LocalServerPort int port;
    @MockBean MobileAuthService mobileAuth;
    @MockBean AdminService admins;
    final HttpClient client = HttpClient.newHttpClient();

    HttpRequest.Builder request(String origin) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/mobile/login"))
                .header("Origin", origin);
    }

    @Test void preflightAllowsConfiguredOriginsAndRequiredMethods() throws Exception {
        for (String origin : new String[]{"http://localhost:5173", "http://127.0.0.1:5173", "http://127.0.0.1:5174"}) {
            for (String method : new String[]{"OPTIONS", "GET", "POST", "PUT", "PATCH", "DELETE"}) {
                var response = client.send(request(origin)
                        .header("Access-Control-Request-Method", method)
                        .header("Access-Control-Request-Headers", "content-type,authorization,accept,x-xsrf-token")
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), response.body());
                assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
                assertEquals("true", response.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow());
            }
        }
        verifyNoInteractions(mobileAuth, admins);
    }

    @Test void wildcardConfigurationIsRejected() {
        var config = new CorsConfig();
        assertThrows(IllegalArgumentException.class, () -> config.corsConfigurer("*"));
        assertThrows(IllegalArgumentException.class, () -> config.corsConfigurer("http://localhost:[*]"));
    }

    @Test void untrustedOriginIsRejectedBeforeLogin() throws Exception {
        var response = client.send(request("http://localhost:58696")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        verifyNoInteractions(mobileAuth);
    }

    @Test void successfulLoginDeserializesJsonAndCreatesSession() throws Exception {
        when(mobileAuth.login(any())).thenReturn(Optional.of(new MobileLoginResponse(
                42L, "Test Customer", "customer@example.com", null, "VERIFIED", "CUSTOMER")));
        var response = login("correct");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"id\":42"));
        assertTrue(response.body().contains("\"role\":\"CUSTOMER\""));
        String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cookie.contains("JSESSIONID="));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertFalse(cookie.contains("; Secure"));
        verify(mobileAuth).login(argThat(r -> "customer@example.com".equals(r.getEmail())
                && "correct".equals(r.getPassword()) && "CUSTOMER".equals(r.getRole())));
    }

    @Test void invalidCredentialsReturn401WithCorsAndNoSession() throws Exception {
        when(mobileAuth.login(any())).thenReturn(Optional.empty());
        var response = login("wrong");
        assertEquals(401, response.statusCode());
        assertTrue(response.headers().firstValue("Set-Cookie").isEmpty());
    }

    HttpResponse<String> login(String password) throws Exception {
        var response = client.send(request("http://localhost:5173").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"customer@example.com\",\"password\":\""
                        + password + "\",\"role\":\"CUSTOMER\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals("http://localhost:5173", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        return response;
    }
}
