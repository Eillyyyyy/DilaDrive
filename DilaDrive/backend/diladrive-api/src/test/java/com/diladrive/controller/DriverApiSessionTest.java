package com.diladrive.controller;

import com.diladrive.config.CorsConfig;
import com.diladrive.dto.AdminProfileResponse;
import com.diladrive.entity.Admin;
import com.diladrive.entity.Driver;
import com.diladrive.service.AdminService;
import com.diladrive.service.DriverService;
import com.diladrive.service.MobileAuthService;
import com.diladrive.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {DriverController.class, VehicleController.class, AuthController.class},
        properties = {"logging.level.root=WARN", "logging.level.org.springframework=WARN", "logging.level.com.diladrive=WARN"})
@Import(CorsConfig.class)
class DriverApiSessionTest {
    @Autowired MockMvc mvc;
    @MockBean DriverService drivers;
    @MockBean VehicleService vehicles;
    @MockBean AdminService admins;
    @MockBean MobileAuthService mobileAuth;

    @BeforeEach
    void records() {
        Driver driver = new Driver();
        driver.setDriverId(7L);
        driver.setDriverName("Database driver");
        when(drivers.getAllDrivers()).thenReturn(List.of(driver));
        when(vehicles.getAllVehicles()).thenReturn(List.of());
    }

    static Stream<Object> acceptedIds() { return Stream.of(3L, 3, "3"); }
    static Stream<Object> rejectedIds() {
        return Stream.of("invalid", "", "99999999999999999999999", "1.5", -1L, 0, 1.5, true);
    }

    @ParameterizedTest @MethodSource("acceptedIds")
    void listAndOptionsAcceptSupportedSessionIdTypes(Object id) throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminId", id);
        mvc.perform(get("/api/drivers").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].driverId").value(7));
        assertInstanceOf(Long.class, session.getAttribute("adminId"));
        session.setAttribute("adminId", id);
        mvc.perform(get("/api/drivers/options").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.driverTypes[0]").value("Company Driver"));
        assertInstanceOf(Long.class, session.getAttribute("adminId"));
        session.setAttribute("adminId", id);
        mvc.perform(get("/api/vehicles").session(session)).andExpect(status().isOk());
    }

    @ParameterizedTest @MethodSource("rejectedIds")
    void invalidSessionValuesAreUnauthorizedRatherThanBadRequests(Object id) throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminId", id);
        mvc.perform(get("/api/drivers").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/drivers/options").session(session)).andExpect(status().isUnauthorized());
        verify(drivers, never()).getAllDrivers();
    }

    @Test
    void driverCreationRequiresVehicleButLegacyProfileUpdateAllowsNullVehicle() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminId", 3L);
        mvc.perform(post("/api/drivers").session(session).contentType("application/json")
                .content("{\"driverName\":\"Driver\",\"driverEmail\":\"driver@example.com\",\"driverPassword\":\"password123\",\"driverPhoneno\":\"0123456789\",\"vehicle\":null}"))
                .andExpect(status().isBadRequest());
        verify(drivers, never()).createDriver(any());
        mvc.perform(put("/api/drivers/7").session(session).contentType("application/json")
                .content("{\"driverName\":\"Driver\",\"driverEmail\":\"driver@example.com\",\"driverPhoneno\":\"0123456789\",\"vehicle\":null}"))
                .andExpect(status().isOk());
        verify(drivers).updateDriverDetails(eq(7L), argThat(request -> request.getVehicle() == null));
    }

    @Test
    void missingAndMobileSessionsCannotAccessAdminRoutes() throws Exception {
        mvc.perform(get("/api/drivers")).andExpect(status().isUnauthorized());
        MockHttpSession mobile = new MockHttpSession();
        mobile.setAttribute("mobileUserId", 3L);
        mobile.setAttribute("mobileRole", "DRIVER");
        mvc.perform(get("/api/drivers/options").session(mobile)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginStoresLongIdAndSessionCanAccessDriverEndpoints() throws Exception {
        Admin admin = new Admin();
        admin.setId(3L);
        admin.setEmail("admin@example.com");
        AdminProfileResponse profile = new AdminProfileResponse(admin);
        when(admins.login("admin@example.com", "password")).thenReturn(true);
        when(admins.getProfile("admin@example.com")).thenReturn(profile);
        when(admins.getProfile(3L)).thenReturn(profile);
        MockHttpSession old = new MockHttpSession();
        old.setAttribute("mobileRole", "CUSTOMER");
        var result = mvc.perform(post("/api/auth/login").session(old)
                .contentType("application/json")
                .content("{\"email\":\"admin@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn();
        assertEquals(true, old.isInvalid());
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertEquals(3L, session.getAttribute("adminId"));
        mvc.perform(get("/api/auth/session").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/drivers").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/drivers/options").session(session)).andExpect(status().isOk());
    }

    @Test
    void corsAllowsCredentialedLocalhostAndPreflight() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminId", 3L);
        mvc.perform(get("/api/drivers").session(session).header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/drivers/options").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void onlyInvalidDynamicIdProducesConversionBadRequest() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminId", 3L);
        mvc.perform(get("/api/drivers/not-a-number").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for request parameter 'id'."));
        mvc.perform(get("/api/drivers/options").session(session)).andExpect(status().isOk());
    }
}
