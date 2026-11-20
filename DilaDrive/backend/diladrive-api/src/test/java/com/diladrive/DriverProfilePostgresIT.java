package com.diladrive;

import com.diladrive.dto.*;
import com.diladrive.entity.Driver;
import com.diladrive.repository.DriverRepository;
import com.diladrive.service.DriverService;
import com.diladrive.service.MobileAuthService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Creates test-owned paired records inside rollback-only tests; existing accounts are never edited. */
@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class DriverProfilePostgresIT {
    @Autowired DriverService service;
    @Autowired DriverRepository drivers;
    @Autowired MobileAuthService auth;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    private static final String PNG="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5x0AAAAASUVORK5CYII=";
    private Driver driver() {
        return service.createDriver(new DriverCreateRequest("Profile QA",UUID.randomUUID()+"@example.test","password123","0123456789",
            "IC","LICENSE","2029-01-01","Company Driver","ACTIVE","AVAILABLE",
            new VehicleCreateRequest("QA Vehicle","QA"+UUID.randomUUID().toString().substring(0,8),"MPV",7,null,null,"AVAILABLE",3)));
    }
    private MockHttpSession session(Driver driver) { var session=new MockHttpSession();session.setAttribute("mobileRole","DRIVER");session.setAttribute("mobileUserId",driver.getDriverId());return session; }

    @Test void pictureAndContactsPersistButAdminFieldsStayReadOnly() throws Exception {
        Driver driver=driver();String plate=service.getDriver(driver.getDriverId()).vehicle().getVehiclePlateno();
        var session=session(driver);
        mvc.perform(put("/api/drivers/me").session(session).contentType("application/json").content(
            "{\"name\":\"Updated Profile\",\"email\":\""+driver.getDriverEmail()+"\",\"phone\":\"0190000000\",\"profileImage\":\""+PNG+"\",\"driverType\":\"EVIL\",\"status\":\"DISABLED\",\"licenseNumber\":\"EVIL\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.vehiclePlate").value(plate)).andExpect(jsonPath("$.totalTrips").value(0));
        em.flush();em.clear();
        Driver saved=drivers.findById(driver.getDriverId()).orElseThrow();
        assertEquals(PNG,saved.getProfileImage());assertEquals("ACTIVE",saved.getDriverStatus());assertEquals("LICENSE",saved.getDriverLicenseno());
        assertEquals("Company Driver",saved.getDriverType());
        mvc.perform(get("/api/drivers/me").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.profileImage").value(PNG)).andExpect(jsonPath("$.name").value("Updated Profile"))
            .andExpect(jsonPath("$.vehicleType").value("MPV"))
            .andExpect(jsonPath("$.vehicleCapacity").value(7))
            .andExpect(jsonPath("$.vehicleStatus").value("AVAILABLE"))
            .andExpect(jsonPath("$.vehicleAssigned").value(true))
            .andExpect(jsonPath("$.vehicleModel").value("QA Vehicle")).andExpect(jsonPath("$.completedTrips").value(0)).andExpect(jsonPath("$.upcomingTrips").value(0));
    }

    @Test void passwordChangeValidatesCurrentPasswordAndNewPasswordWorks() throws Exception {
        Driver driver=driver();var session=session(driver);
        mvc.perform(post("/api/drivers/me/password").session(session).contentType("application/json")
            .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"new-password123\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Current password is incorrect."));
        mvc.perform(post("/api/drivers/me/password").session(session).contentType("application/json")
            .content("{\"currentPassword\":\"password123\",\"newPassword\":\"new-password123\"}"))
            .andExpect(status().isNoContent());
        em.flush();em.clear();
        assertNotEquals("new-password123",drivers.findById(driver.getDriverId()).orElseThrow().getDriverPassword());
        MobileLoginRequest login=new MobileLoginRequest();login.setRole("DRIVER");login.setEmail(driver.getDriverEmail());login.setPassword("new-password123");
        assertTrue(auth.login(login).isPresent());login.setPassword("password123");assertTrue(auth.login(login).isEmpty());
    }

    @Test void rejectsInvalidPhotosAndCrossRoleProfileAccess() throws Exception {
        Driver driver=driver();
        mvc.perform(put("/api/drivers/me").session(session(driver)).contentType("application/json").content(
            "{\"name\":\"Profile QA\",\"email\":\""+driver.getDriverEmail()+"\",\"phone\":\"0123456789\",\"profileImage\":\"data:image/png;base64,bm90LWltYWdl\"}"))
            .andExpect(status().isBadRequest());
        var customer=new MockHttpSession();customer.setAttribute("mobileRole","CUSTOMER");customer.setAttribute("mobileUserId",driver.getDriverId());
        mvc.perform(get("/api/drivers/me").session(customer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/drivers/me/password").session(customer).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }
}
