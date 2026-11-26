package com.diladrive;

import com.diladrive.repository.DocumentRepository;
import com.diladrive.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/DilaDrive",
        "spring.datasource.username=postgres",
        "spring.jpa.hibernate.ddl-auto=validate",
        "booking.push-enabled=false"
})
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DEV_HARDENING_IT", matches = "true")
class DevHardeningPostgresTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entities;
    @Autowired DocumentRepository documents;
    @Autowired PaymentRepository payments;

    @Test
    void customerCannotReadAnotherCustomersBookingTimelineDocumentsOrProof() throws Exception {
        long requester = customer("normal");
        long owner = customer("company");
        long booking = id("select booking_id from booking where cust_id=? and booking_pickupaddress='DEV-SEED Pickup B'", owner);
        jdbc.update("update customer set cust_type='COMPANY' where cust_id=?", requester);
        long document = id("""
                insert into documents (document_type,document_number,booking_id,cust_id,cust_name,amount,status)
                values ('INVOICE',?,?,?,'DEV-SEED Company',100,'ISSUED') returning document_id
                """, "FIX-" + UUID.randomUUID().toString().substring(0, 12), booking, owner);
        long payment = id("""
                insert into payment (booking_id,amount,payment_method,receipt_file_url)
                values (?,100,'BANK_TRANSFER','fixture-proof.png') returning payment_id
                """, booking);
        assertTrue(documents.findByDocumentIdAndCustId(document, owner).isPresent());
        assertTrue(payments.findByPaymentIdAndBookingCustId(payment, owner).isPresent());

        var own = session("CUSTOMER", owner);
        mvc.perform(get("/api/bookings/mine/{id}", booking).session(own)).andExpect(status().isOk());
        mvc.perform(get("/api/bookings/{id}/timeline", booking).session(own)).andExpect(status().isOk());
        var other = session("CUSTOMER", requester);
        for (String path : new String[]{"/api/bookings/mine/" + booking, "/api/bookings/" + booking + "/timeline",
                "/api/documents/customer/" + document + "/pdf", "/api/payments/" + payment + "/proof"}) {
            mvc.perform(get(path).session(other)).andExpect(status().isNotFound());
        }
    }

    @Test
    void driverCannotReadOrChangeAnotherDriversAssignmentPhotosOrNotifications() throws Exception {
        long owner = driver(1);
        long other = driver(2);
        long assignment = id("select assignment_id from driver_assignment where driver_id=?", owner);
        long photo = id("""
                insert into assignment_verification_photo(assignment_id,photo_type,file_url)
                values (?,'PICKUP','fixture-photo.png') returning photo_id
                """, assignment);
        long notice = id("""
                insert into notification(recipient_role,recipient_id,driver_id,notification_title,notification_message)
                values ('DRIVER',?,?,'Fixture ownership','Fixture only') returning notification_id
                """, owner, owner);
        var own = session("DRIVER", owner);
        var foreign = session("DRIVER", other);
        mvc.perform(get("/api/assignments/mine/{id}", assignment).session(own)).andExpect(status().isOk());
        mvc.perform(get("/api/assignments/{id}/photos", assignment).session(own)).andExpect(status().isOk());
        mvc.perform(get("/api/assignments/mine/{id}", assignment).session(foreign)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/assignments/{id}/status", assignment).session(foreign)
                .contentType("application/json").content("{\"status\":\"OTW\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/assignments/{id}/photos", assignment).session(foreign)).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/assignments/{id}/photos", assignment).file("file", new byte[]{1})
                .param("photoType", "PICKUP").session(foreign)).andExpect(status().isNotFound());
        mvc.perform(get("/api/assignments/{id}/photos/{photo}/content", assignment, photo).session(foreign))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/notifications/driver/{id}", owner).session(own)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Fixture ownership")));
        mvc.perform(get("/api/notifications/driver/{id}", other).session(foreign)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Fixture ownership"))));
        mvc.perform(get("/api/notifications/driver/{id}", owner).session(foreign)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/notifications/{id}/read", notice).session(foreign)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/notifications/{id}/read", notice).session(own)).andExpect(status().isOk());
    }

    @Test
    void pushTokenCannotBeReboundOrDeletedByAnotherRecipient() throws Exception {
        long owner = customer("normal");
        var own = session("CUSTOMER", owner);
        var other = session("DRIVER", driver(2));
        String token = "fixture-" + UUID.randomUUID();
        String replacement = "fixture-" + UUID.randomUUID();
        String payload = "{\"token\":\"" + token + "\",\"recipientId\":999,\"recipientRole\":\"DRIVER\"}";
        mvc.perform(post("/api/mobile/push-token").contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/mobile/push-token").session(own).contentType("application/json").content(payload))
                .andExpect(status().isOk());
        mvc.perform(post("/api/mobile/push-token").session(own).contentType("application/json").content(payload))
                .andExpect(status().isOk());
        mvc.perform(post("/api/mobile/push-token").session(other).contentType("application/json").content(payload))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/mobile/push-token").session(other).contentType("application/json").content(payload))
                .andExpect(status().isOk());
        assertEquals(1L, id("select count(*) from mobile_push_token where token=? and recipient_role='CUSTOMER' and recipient_id=?", token, owner));
        mvc.perform(post("/api/mobile/push-token").session(own).contentType("application/json")
                .content("{\"token\":\"" + replacement + "\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/mobile/push-token").session(own).contentType("application/json").content(payload))
                .andExpect(status().isOk());
        assertEquals(0L, id("select count(*) from mobile_push_token where token=?", token));
        assertEquals(1L, id("select count(*) from mobile_push_token where token=? and recipient_id=?", replacement, owner));
    }

    @Test
    void profileAndPasswordRoutesIgnoreProtectedFields() throws Exception {
        long selfCustomer = customer("pending");
        long otherCustomer = customer("normal");
        long selfDriver = driver(1);
        long otherDriver = driver(2);
        String forged = "\"role\":\"ADMIN\",\"status\":\"DISABLED\",\"driverStatus\":\"DISABLED\","
                + "\"custStatus\":\"ACTIVE\",\"approvalStatus\":\"APPROVED\",\"emailVerified\":true,"
                + "\"verifiedAt\":\"2099-01-01T00:00:00Z\",\"verifiedByAdminId\":999";
        var customerSession = session("CUSTOMER", selfCustomer);
        mvc.perform(put("/api/customers/me").session(customerSession)
                .contentType("application/json").content("{\"id\":" + otherCustomer + ",\"custId\":" + otherCustomer
                        + ",\"name\":\"Fixture Edited Customer\",\"email\":\"hardening-pending@example.invalid\"," + forged + "}"))
                .andExpect(status().isOk());
        entities.flush();
        assertEquals(1L, id("""
                select count(*) from customer where cust_id=? and cust_name='Fixture Edited Customer'
                and cust_status='PENDING' and approval_status is null and verified_at is null and verified_by_admin_id is null
                """, selfCustomer));
        assertEquals(1L, id("select count(*) from customer where cust_id=? and cust_name='DEV-SEED Customer'", otherCustomer));

        var encoder = new BCryptPasswordEncoder();
        jdbc.update("update driver set driver_password=? where driver_id=?", encoder.encode("FixtureCurrent1"), selfDriver);
        String otherHash = jdbc.queryForObject("select driver_password from driver where driver_id=?", String.class, otherDriver);
        var driverSession = session("DRIVER", selfDriver);
        String ids = "\"id\":" + otherDriver + ",\"driverId\":" + otherDriver;
        mvc.perform(put("/api/drivers/me").session(driverSession).contentType("application/json")
                .content("{" + ids + ",\"name\":\"Fixture Edited Driver\",\"email\":\"hardening-driver1@example.invalid\","
                        + "\"phone\":\"0123456789\"," + forged + "}")).andExpect(status().isOk());
        mvc.perform(post("/api/drivers/me/password").session(driverSession).contentType("application/json")
                .content("{" + ids + ",\"email\":\"changed@example.invalid\",\"currentPassword\":\"FixtureCurrent1\","
                        + "\"newPassword\":\"FixtureChanged2\"," + forged + "}")).andExpect(status().isNoContent());
        entities.flush();
        assertEquals(1L, id("""
                select count(*) from driver where driver_id=? and driver_name='Fixture Edited Driver'
                and driver_email='hardening-driver1@example.invalid' and driver_status='ACTIVE'
                """, selfDriver));
        assertTrue(encoder.matches("FixtureChanged2",
                jdbc.queryForObject("select driver_password from driver where driver_id=?", String.class, selfDriver)));
        assertTrue(otherHash.equals(jdbc.queryForObject("select driver_password from driver where driver_id=?", String.class, otherDriver)));
        assertEquals("CUSTOMER", customerSession.getAttribute("mobileRole"));
        assertEquals("DRIVER", driverSession.getAttribute("mobileRole"));
    }

    private long customer(String fixture) {
        return id("select cust_id from customer where cust_email=?", "hardening-" + fixture + "@example.invalid");
    }

    private long driver(int fixture) {
        return id("select driver_id from driver where driver_email=?", "hardening-driver" + fixture + "@example.invalid");
    }

    private long id(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    private static MockHttpSession session(String role, long owner) {
        var session = new MockHttpSession();
        session.setAttribute("mobileRole", role);
        session.setAttribute("mobileUserId", owner);
        return session;
    }
}
