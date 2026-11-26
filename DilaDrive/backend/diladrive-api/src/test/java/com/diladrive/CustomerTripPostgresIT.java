package com.diladrive;

import com.diladrive.dto.*;
import com.diladrive.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.core.env.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** All fixtures and migrations run in a unique disposable PostgreSQL schema. */
@SpringBootTest(properties={"spring.jpa.show-sql=false","logging.level.root=ERROR","booking.push-enabled=false",
        "booking.pickup-check-enabled=false","booking.reservation-sweep-ms=3600000"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="RUN_CUSTOMER_TRIP_IT", matches="true")
class CustomerTripPostgresIT {
    private static final String APPROVE = "{\"decision\":\"APPROVE\"}";
    private static final String REJECT = "{\"decision\":\"REJECT\",\"reason\":\"  Registration could not be verified.  \"}";

    @org.springframework.boot.test.mock.mockito.SpyBean BookingPushService push;

    private Long reviewer(String role, String status) {
        return jdbc.queryForObject("insert into admin(admin_name,admin_role,admin_status) values('Reviewer',?,?) returning admin_id",
                Long.class, role, status);
    }

    private void pending(String category) {
        jdbc.update("""
                update customer set cust_type=?, cust_status='PENDING', cust_email='verification@example.test',
                    cust_companyname='Company', company_registration_number='REG1', cust_password='existing-hash',
                    cust_profile_image='existing-photo' where cust_id=?
                """, category, customer);
    }

    private org.springframework.test.web.servlet.ResultActions decide(Long reviewer, Long target, String payload) throws Exception {
        return mvc.perform(patch("/api/customers/{id}/verification", target)
                .sessionAttr("adminId", reviewer).contentType("application/json").content(payload));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"NORMAL, ADMIN", "COMPANY, ADMIN", "NORMAL, SUPER_ADMIN"})
    void approvesBothCategoriesAndBothActiveAdminRoles(String category, String role) throws Exception {
            pending(category);
            Long admin = reviewer(role, "ACTIVE");
            decide(admin, customer, APPROVE).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.category").value(category))
                    .andExpect(jsonPath("$.reviewerId").value(admin))
                    .andExpect(jsonPath("$.verifiedAt").isNotEmpty())
                    .andExpect(jsonPath("$.custPassword").doesNotExist());
            assertEquals(1, jdbc.queryForObject("""
                    select count(*) from customer_verification_history h join customer c on c.cust_id=h.customer_id
                    where h.customer_id=? and h.admin_id=? and h.decision='APPROVE'
                    and h.previous_status='PENDING' and h.new_status='ACTIVE' and c.verified_at=h.decided_at
                    and c.verified_by_admin_id=h.admin_id and c.cust_name='QA Customer'
                    and c.cust_email='verification@example.test' and c.cust_phoneno='0123456789'
                    and c.cust_companyname='Company' and c.company_registration_number='REG1'
                    and c.cust_profile_image='existing-photo' and c.cust_password='existing-hash'
                    """, Integer.class, customer, admin));
            decide(admin, customer, APPROVE).andExpect(status().isConflict());
            decide(admin, customer, REJECT).andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("select count(*) from notification where recipient_role='CUSTOMER' and recipient_id=? and notification_type='ACCOUNT_VERIFICATION'", Integer.class, customer));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"NORMAL", "COMPANY"})
    void rejectsBothCategoriesAndKeepsReasonsAndInboxPrivate(String category) throws Exception {
        Long admin = reviewer("ADMIN", "ACTIVE");
        long other = jdbc.queryForObject("insert into customer(cust_status) values('ACTIVE') returning cust_id", Long.class);
            pending(category);
            decide(admin, customer, REJECT).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectionReason").value("Registration could not be verified."));
            decide(admin, customer, APPROVE).andExpect(status().isConflict());
            decide(admin, customer, REJECT).andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from customer_verification_history
                where customer_id=? and admin_id=? and new_status='REJECTED'
                and rejection_reason='Registration could not be verified.'
                """, Integer.class, customer, admin));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from customer where cust_id=? and verified_by_admin_id=?
                and verified_at is not null and rejection_reason='Registration could not be verified.'
                """, Integer.class, customer, admin));
        mvc.perform(get("/api/customers/me").session(mobileSession(customer, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").value("Registration could not be verified."));
        mvc.perform(get("/api/customers/me").session(mobileSession(other, "CUSTOMER")).param("id", customer.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").doesNotExist());
        mvc.perform(get("/api/notifications/customer/mine").session(mobileSession(other, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/notifications/customer/mine").session(mobileSession(customer, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].message").value("Your DilaDrive account was rejected. Reason: Registration could not be verified."));
        Long notice = jdbc.queryForObject("select max(notification_id) from notification where recipient_role='CUSTOMER' and recipient_id=?",
                Long.class, customer);
        mvc.perform(patch("/api/notifications/{id}/read", notice).session(mobileSession(other, "CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/notifications/{id}/read", notice).sessionAttr("adminId", admin))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/notifications/{id}/read", notice).session(mobileSession(customer, "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
        mvc.perform(post("/api/auth/mobile/login").contentType("application/json").content("""
                {"email":"verification@example.test","password":"existing-hash","role":"CUSTOMER"}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(put("/api/customers/me").session(mobileSession(customer, "CUSTOMER"))
                        .contentType("application/json")
                        .content("{\"name\":\"Updated\",\"email\":\"verification@example.test\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test void rejectsBadRequestsAndCannotBypassVerificationThroughProfileEdits() throws Exception {
        pending("NORMAL");
        Long admin = reviewer("ADMIN", "ACTIVE");
        for (String payload : List.of(
                "{\"decision\":\"OTHER\"}", "{\"decision\":\"REJECT\"}",
                "{\"decision\":\"REJECT\",\"reason\":\"\"}", "{\"decision\":\"REJECT\",\"reason\":\"   \"}",
                "{\"decision\":\"REJECT\",\"reason\":\"" + "x".repeat(501) + "\"}",
                "{\"decision\":\"APPROVE\",\"reason\":\"not applicable\"}",
                "{\"decision\":\"APPROVE\",\"reviewerId\":9999}")) {
            decide(admin, customer, payload).andExpect(status().isBadRequest());
        }
        decide(admin, Long.MAX_VALUE, APPROVE).andExpect(status().isNotFound());
        decide(reviewer("ADMIN", "INACTIVE"), customer, APPROVE).andExpect(status().isForbidden());
        decide(Long.MAX_VALUE, customer, APPROVE).andExpect(status().isForbidden());
        mvc.perform(patch("/api/customers/{id}/verification", customer).contentType("application/json").content(APPROVE))
                .andExpect(status().isUnauthorized());
        for (var session : List.of(mobileSession(customer, "CUSTOMER"), mobileSession(driver, "DRIVER"))) {
            mvc.perform(patch("/api/customers/{id}/verification", customer).session(session)
                            .contentType("application/json").content(APPROVE)).andExpect(status().isUnauthorized());
        }
        mvc.perform(put("/api/customers/{id}", customer).sessionAttr("adminId", admin)
                        .contentType("application/json").content("{\"custStatus\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/customers/{id}", customer).sessionAttr("adminId", admin)
                        .contentType("application/json")
                        .content("{\"custName\":\"Admin edit\",\"custEmail\":\"verification@example.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.custStatus").value("PENDING"));
        mvc.perform(put("/api/customers/me").session(mobileSession(customer, "CUSTOMER"))
                        .contentType("application/json")
                        .content("{\"name\":\"Edited\",\"email\":\"verification@example.test\",\"custStatus\":\"ACTIVE\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        for (String persisted : List.of("UNKNOWN", "   ")) {
            jdbc.update("update customer set cust_status=? where cust_id=?", persisted, customer);
            decide(admin, customer, APPROVE).andExpect(status().isConflict());
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from customer_verification_history", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from notification", Integer.class));
    }

    @Test void rollsBackDecisionWhenInboxInsertFails() throws Exception {
        pending("NORMAL");
        Long admin = reviewer("ADMIN", "ACTIVE");
        jdbc.execute("alter table notification add constraint reject_verification_notice check (notification_type <> 'ACCOUNT_VERIFICATION')");
        try {
            decide(admin, customer, REJECT).andExpect(status().isConflict());
            assertEquals(1, jdbc.queryForObject("""
                    select count(*) from customer where cust_id=? and cust_status='PENDING'
                    and verified_at is null and verified_by_admin_id is null and rejection_reason is null
                    """, Integer.class, customer));
            assertEquals(0, jdbc.queryForObject("select count(*) from customer_verification_history", Integer.class));
            assertEquals(0, jdbc.queryForObject("select count(*) from notification", Integer.class));
        } finally {
            jdbc.execute("alter table notification drop constraint reject_verification_notice");
        }
    }

    @Test void concurrentDecisionsProduceOneHistoryAndOneInboxRecord() throws Exception {
        pending("NORMAL");
        Long admin = reviewer("ADMIN", "ACTIVE");
        Long otherAdmin = reviewer("SUPER_ADMIN", "ACTIVE");
        var ready = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var approve = pool.submit(() -> {
                ready.await(5, TimeUnit.SECONDS);
                return decide(admin, customer, APPROVE).andReturn().getResponse().getStatus();
            });
            var reject = pool.submit(() -> {
                ready.await(5, TimeUnit.SECONDS);
                return decide(otherAdmin, customer, REJECT).andReturn().getResponse().getStatus();
            });
            assertEquals(Set.of(200, 409), Set.of(approve.get(15, TimeUnit.SECONDS), reject.get(15, TimeUnit.SECONDS)));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_verification_history where customer_id=?", Integer.class, customer));
        assertEquals(1, jdbc.queryForObject("select count(*) from notification where recipient_id=? and notification_type='ACCOUNT_VERIFICATION'", Integer.class, customer));
    }

    @Test void optionalPushFailureOccursAfterDecisionAndInboxCommit() throws Exception {
        pending("NORMAL");
        Long admin = reviewer("ADMIN", "ACTIVE");
        var attempted = new CountDownLatch(1);
        var committed = new java.util.concurrent.atomic.AtomicBoolean();
        var unavailable = mock(JdbcTemplate.class);
        when(unavailable.queryForList(anyString(), eq(String.class), eq("CUSTOMER"), eq(customer)))
                .thenAnswer(call -> {
                    committed.set(1 == jdbc.queryForObject("""
                            select count(*) from customer c join notification n on n.recipient_id=c.cust_id
                            where c.cust_id=? and c.cust_status='REJECTED' and n.notification_type='ACCOUNT_VERIFICATION'
                            """, Integer.class, customer));
                    attempted.countDown();
                    throw new org.springframework.dao.DataAccessResourceFailureException("Simulated push infrastructure failure");
                });
        try (var enabledPush = new BookingPushService(unavailable, true, "")) {
            doAnswer(call -> {
                enabledPush.notifyAfterCommit(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3));
                return null;
            }).when(push).notifyAfterCommit(eq("CUSTOMER"), eq(customer), anyString(),
                    eq("Open DilaDrive to view your account verification result."));
            decide(admin, customer, REJECT).andExpect(status().isOk());
            assertTrue(attempted.await(5, TimeUnit.SECONDS));
            assertTrue(committed.get());
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_verification_history where customer_id=?", Integer.class, customer));
    }

    @Test void adminSelfUpdatePreservesProtectedAndOmittedFieldsAndSupportsExplicitClear() throws Exception {
        long admin = jdbc.queryForObject("""
                insert into admin(admin_name, admin_email, admin_phone, admin_address, admin_profile_image, admin_password)
                values ('Original', 'uc003-admin@example.test', '0123456789', 'Address', 'newer-photo', 'hash')
                returning admin_id
                """, Long.class);
        jdbc.update("insert into admin(admin_email) values ('uc003-taken@example.test')");
        String request = """
                {"name":"Updated","email":"uc003-admin@example.test","id":99999,"role":"SUPER_ADMIN",
                "status":"DISABLED","verificationStatus":"ACTIVE","passwordHash":"untrusted",
                "vehicleId":4,"assignmentId":5}
                """;
        mvc.perform(put("/api/auth/profile").contentType("application/json").content(request))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/auth/profile").sessionAttr("adminId", admin)
                        .contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.role").value("ADMIN")).andExpect(jsonPath("$.phone").value("0123456789"))
                .andExpect(jsonPath("$.address").value("Address")).andExpect(jsonPath("$.profileImage").value("newer-photo"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertEquals("ACTIVE", jdbc.queryForObject("select admin_status from admin where admin_id=?", String.class, admin));
        assertEquals("hash", jdbc.queryForObject("select admin_password from admin where admin_id=?", String.class, admin));
        mvc.perform(put("/api/auth/profile").sessionAttr("adminId", admin).contentType("application/json")
                        .content(request.replace("uc003-admin", "uc003-taken")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/auth/profile").sessionAttr("adminId", admin).contentType("application/json")
                        .content(request.replaceFirst("\\{", "{\"phone\":\"01abc\",")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/auth/profile").sessionAttr("adminId", admin).contentType("application/json")
                        .content(request.replaceFirst("\\{", "{\"clearPhone\":true,\"clearAddress\":true,\"profileImage\":\"\",")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist()).andExpect(jsonPath("$.profileImage").doesNotExist());
        jdbc.update("update admin set admin_status='INACTIVE' where admin_id=?", admin);
        mvc.perform(put("/api/auth/profile").sessionAttr("adminId", admin)
                        .contentType("application/json").content(request)).andExpect(status().isUnauthorized());
    }

    @Test void driverSelfUpdatePreservesOperationalDataAndRejectsDeactivatedSession() throws Exception {
        jdbc.update("update driver set driver_profile_image='newer-photo', driver_licenseno='LIC1', driver_password='hash' where driver_id=?", driver);
        var session = mobileSession(driver, "DRIVER");
        String request = """
                {"name":"Driver Updated","email":"self-driver@example.test","phone":"+60 12-345 6789",
                "id":99999,"role":"SUPER_ADMIN","status":"DISABLED","verificationStatus":"ACTIVE",
                "passwordHash":"untrusted","vehicleId":9,"assignmentId":9,"licenseNumber":"BAD","availability":"BUSY"}
                """;
        mvc.perform(put("/api/drivers/me").session(mobileSession(customer, "CUSTOMER"))
                        .contentType("application/json").content(request)).andExpect(status().isForbidden());
        mvc.perform(put("/api/drivers/me").session(session).contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Driver Updated"))
                .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.licenseNumber").value("LIC1")).andExpect(jsonPath("$.vehicleAssigned").value(true))
                .andExpect(jsonPath("$.profileImage").value("newer-photo"));
        assertEquals("hash", jdbc.queryForObject("select driver_password from driver where driver_id=?", String.class, driver));
        assertEquals(driver, jdbc.queryForObject("select driver_id from vehicle where vehicle_id=?", Long.class, vehicle));
        mvc.perform(put("/api/drivers/me").session(session).contentType("application/json")
                        .content(request.replaceFirst("\\{", "{\"profileImage\":\"\",")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profileImage").doesNotExist());
        jdbc.update("update driver set driver_status='DISABLED' where driver_id=?", driver);
        mvc.perform(put("/api/drivers/me").session(session).contentType("application/json")
                        .content(request.replace("Driver Updated", "Blocked")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DRIVER_ACCOUNT_DEACTIVATED"));
        assertEquals("Driver Updated", jdbc.queryForObject("select driver_name from driver where driver_id=?", String.class, driver));
    }

    @Test void pendingCustomerKeepsOptionalAndProtectedFieldsAndCorporateBlanksAreRejected() throws Exception {
        jdbc.update("update customer set cust_type='NORMAL', cust_status='PENDING', cust_profile_image='newer-photo', cust_password='hash' where cust_id=?", customer);
        var session = mobileSession(customer, "CUSTOMER");
        String request = """
                {"name":"Customer Updated","email":"self-customer@example.test","id":99999,"role":"ADMIN",
                "status":"ACTIVE","verificationStatus":"ACTIVE","type":"COMPANY","passwordHash":"untrusted",
                "vehicleId":9,"assignmentId":9}
                """;
        mvc.perform(put("/api/customers/me").session(session).contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(customer))
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.type").value("NORMAL"))
                .andExpect(jsonPath("$.phone").value("0123456789")).andExpect(jsonPath("$.profileImage").value("newer-photo"));
        assertEquals("hash", jdbc.queryForObject("select cust_password from customer where cust_id=?", String.class, customer));
        mvc.perform(put("/api/customers/me").session(session).contentType("application/json")
                        .content(request.replaceFirst("\\{", "{\"companyName\":\"Unauthorised\",")))
                .andExpect(status().isForbidden());
        jdbc.update("update customer set cust_type='COMPANY', cust_companyname='Company', company_registration_number='REG1' where cust_id=?", customer);
        for (String field : List.of("companyName", "companyRegistrationNumber")) {
            mvc.perform(put("/api/customers/me").session(session).contentType("application/json")
                            .content(request.replace("Customer Updated", "Uncommitted")
                                    .replaceFirst("\\{", "{\"" + field + "\":\"   \",")))
                    .andExpect(status().isBadRequest());
        }
        assertEquals("Customer Updated", jdbc.queryForObject("select cust_name from customer where cust_id=?", String.class, customer));
        mvc.perform(put("/api/customers/me").session(session).contentType("application/json")
                        .content(request.replaceFirst("\\{", "{\"profileImage\":\"data:image/png;base64,iVBORw0KGgo=\",")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/customers/me").session(session).contentType("application/json")
                        .content(request.replaceFirst("\\{", "{\"phone\":null,\"profileImage\":null,\"companyName\":\"Updated Company\",")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("0123456789"))
                .andExpect(jsonPath("$.profileImage").value("newer-photo")).andExpect(jsonPath("$.companyName").value("Updated Company"))
                .andExpect(jsonPath("$.companyRegistrationNumber").value("REG1"));
        String png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5x0AAAAASUVORK5CYII=";
        mvc.perform(put("/api/customers/me").session(session).contentType("application/json")
                        .content(request.replaceFirst("\\{", "{\"phone\":\"  \",\"profileImage\":\"" + png + "\",")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("0123456789"))
                .andExpect(jsonPath("$.profileImage").value(png));
        mvc.perform(put("/api/customers/me").session(session).contentType("application/json")
                        .content(request.replaceFirst("\\{", "{\"clearPhone\":true,\"profileImage\":\"\",")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.profileImage").doesNotExist());
    }

    @Test void selfProfileReadsUseCurrentDatabaseRowsAndHideCredentials() throws Exception {
        long admin = jdbc.queryForObject("""
                insert into admin(admin_name, admin_status, admin_password)
                values ('Original Admin', 'ACTIVE', 'sensitive') returning admin_id
                """, Long.class);
        var adminSession = new org.springframework.mock.web.MockHttpSession();
        adminSession.setAttribute("adminId", admin);
        mvc.perform(get("/api/auth/session").session(adminSession).param("id", "99999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Original Admin"))
                .andExpect(jsonPath("$.password").doesNotExist());
        jdbc.update("update admin set admin_name='Fresh Admin' where admin_id=?", admin);
        mvc.perform(get("/api/auth/session").session(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Fresh Admin"));
        jdbc.update("update admin set admin_status='INACTIVE' where admin_id=?", admin);
        mvc.perform(get("/api/auth/session").session(adminSession)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/session").sessionAttr("adminId", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());

        var driverSession = mobileSession(driver, "DRIVER");
        mvc.perform(get("/api/drivers/me").session(driverSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(driver));
        jdbc.update("update driver set driver_name='Fresh Driver' where driver_id=?", driver);
        mvc.perform(get("/api/drivers/me").session(driverSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Fresh Driver"))
                .andExpect(jsonPath("$.driverIcno").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.vehicleAssigned").value(true));

        var customerSession = mobileSession(customer, "CUSTOMER");
        jdbc.update("update customer set cust_type='NORMAL', cust_status='PENDING' where cust_id=?", customer);
        mvc.perform(get("/api/customers/me").session(customerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("NORMAL"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.companyName").doesNotExist());
        jdbc.update("""
                update customer set cust_name='Fresh Customer', cust_type='COMPANY',
                cust_companyname='Fresh Company', company_registration_number='REG123' where cust_id=?
                """, customer);
        mvc.perform(get("/api/customers/me").session(customerSession).param("id", "99999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(customer))
                .andExpect(jsonPath("$.name").value("Fresh Customer"))
                .andExpect(jsonPath("$.companyName").value("Fresh Company"))
                .andExpect(jsonPath("$.companyRegistrationNumber").value("REG123"))
                .andExpect(jsonPath("$.custPassword").doesNotExist());
        mvc.perform(get("/api/drivers/me").session(customerSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/customers/me").session(driverSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/drivers/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customers/me")).andExpect(status().isUnauthorized());
    }

    @Test void driverDisabledAfterSessionEstablishmentCannotReachProtectedHandlers() throws Exception {
        var session = mobileSession(driver, "DRIVER");
        mvc.perform(get("/api/assignments/mine").session(session)).andExpect(status().isOk());
        jdbc.update("update driver set driver_status='DISABLED' where driver_id=?", driver);
        mvc.perform(get("/api/assignments/mine").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DRIVER_ACCOUNT_DEACTIVATED"));
        mvc.perform(get("/api/assignments/mine").session(session)).andExpect(status().isUnauthorized());
        var requests = List.of(
                get("/api/drivers/me"), put("/api/drivers/me"), post("/api/drivers/me/password"),
                get("/api/assignments/mine"), get("/api/assignments/1"),
                patch("/api/assignments/1/status"), get("/api/assignments/1/photos"),
                multipart("/api/assignments/1/photos"), get("/api/assignments/1/photos/1/content"),
                post("/api/payments/bookings/1/cash"), get("/api/notifications/driver/" + driver),
                patch("/api/notifications/1/read"), post("/api/mobile/push-token"),
                delete("/api/mobile/push-token"));
        for (var request : requests) {
            // Each case represents a pre-existing driver session.
            session = mobileSession(driver, "DRIVER");
            session.setAttribute("adminId", 99L);
            mvc.perform(request.session(session))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("DRIVER_ACCOUNT_DEACTIVATED"));
            assertNull(session.getAttribute("mobileUserId"));
            assertNull(session.getAttribute("mobileRole"));
            assertEquals(99L, session.getAttribute("adminId"));
        }
        jdbc.update("update driver set driver_status='INACTIVE' where driver_id=?", driver);
        mvc.perform(get("/api/drivers/me").session(mobileSession(driver, "DRIVER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DRIVER_ACCOUNT_DEACTIVATED"));
    }

    private static org.springframework.mock.web.MockHttpSession mobileSession(long id, String role) {
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("mobileUserId", id);
        session.setAttribute("mobileRole", role);
        return session;
    }
    static String schema="diladrive_trip_check_"+System.currentTimeMillis();
    static String url,user,password;
    @org.junit.jupiter.api.io.TempDir static Path fleetStorage;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        var properties=new Properties();
        try(var input=Files.newInputStream(Path.of("src/main/resources/application.properties"))) { properties.load(input); }
        var env=new StandardEnvironment();
        env.getPropertySources().addLast(new PropertiesPropertySource("application",properties));
        url=env.getProperty("spring.datasource.url"); user=env.getProperty("spring.datasource.username"); password=env.getProperty("spring.datasource.password");
        try(var c=DriverManager.getConnection(url,user,password); var s=c.createStatement()) {
            s.execute("create schema "+schema); s.execute("set search_path to "+schema);
            for(String file:List.of("BASELINE_CURRENT.sql","020_one_driver_one_vehicle.sql","021_driver_profile_image.sql","022_active_booking_assignment.sql","023_customer_booking_flow.sql","024_pending_without_reservation.sql","025_distance_pricing.sql","026_trip_charges_and_commission.sql","028_customer_account_verification.sql","029_booking_flight_schedule.sql","030_booking_assignment_preference.sql","031_pending_pair_reservations.sql"))
                s.execute(Files.readString(Path.of("../../database/schema/"+file)));
        }
        registry.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+schema);
        registry.add("spring.datasource.username",()->user); registry.add("spring.datasource.password",()->password);
        registry.add("app.fleet-documents.storage-dir", () -> fleetStorage.toString());
        registry.add("app.driver-data.master-key", () -> java.util.Base64.getEncoder().encodeToString(new byte[32]));
    }
    @AfterAll static void cleanup() throws Exception {
        if(url!=null && schema.matches("diladrive_trip_check_[0-9]+"))
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()) {s.execute("drop schema "+schema+" cascade");}
    }
    @Autowired CustomerTripService trips;
    @Autowired BookingService bookings;
    @Autowired AssignmentServiceImpl assignments;
    @Autowired BookingPickupCheck pickupCheck;
    @Autowired BookingPriceService bookingPrices;
    @Autowired TripFinanceService finance;
    @Autowired PricingService pricing;
    @Autowired com.diladrive.service.NotificationService inboxNotifications;
    @Autowired com.diladrive.service.FleetExpiryReminderScheduler expiryReminders;
    @Autowired com.diladrive.service.ResourceUnavailabilityService periods;
    @Autowired com.diladrive.service.UpcomingTripReminderScheduler tripReminders;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @MockBean RoadRoutingService routing;
    Long customer,driver,vehicle;
    LocalDateTime start=LocalDateTime.of(2035,1,1,10,0);
    @BeforeEach void fixture() {
        assertEquals(schema,jdbc.queryForObject("select current_schema()",String.class));
        jdbc.execute("truncate booking_vehicle_hold,booking_request_idempotency,notification,driver_assignment,booking,vehicle_unavailable_period,driver_unavailable_period,vehicle,driver,customer,booking_fare_rate restart identity cascade");
        customer=jdbc.queryForObject("insert into customer(cust_name,cust_phoneno,cust_status) values('QA Customer','0123456789','ACTIVE') returning cust_id",Long.class);
        driver=addVehicle("Sedan",4,4);
        vehicle=jdbc.queryForObject("select vehicle_id from vehicle where driver_id=?",Long.class,driver);
        when(routing.route(any())).thenReturn(new RoadRoutingService.Route(58,7200,List.of(List.of(101.0,3.0),List.of(102.0,4.0))));
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    Long addVehicle(String type,int seats,int luggage) {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
        Long d=jdbc.queryForObject("insert into driver(driver_name,driver_status,driver_availability) values('QA Driver','ACTIVE','AVAILABLE') returning driver_id",Long.class);
        jdbc.update("insert into vehicle(driver_id,vehicle_type,vehicle_capacity,vehicle_luggagecapacity,vehicle_status,vehicle_plateno) values(?,?,?,?,?,?)",d,type,seats,luggage,"AVAILABLE","QA"+d);
        jdbc.update("insert into booking_fare_rate(vehicle_type,base_fare,rate_per_km,additional_charge,active) values(?,10,2,0,true) on conflict do nothing",type);
        return d;
        });
    }
    TripQuoteRequest request(int passengers,int small,int medium,int large) {
        return new TripQuoteRequest("QA pickup","QA destination",3.0,101.0,4.0,102.0,start,passengers,small,medium,large,"NORMAL_TRANSFER",null,null);
    }
    TripQuoteRequest approved(TripQuoteRequest r) { var q=trips.quote(r); return r.accept(q.recommendedVehicleType(),q.quoteToken()); }
    Map<String,Object> create() { return trips.create(customer,approved(request(3,1,1,0)),UUID.randomUUID().toString()); }
    @Test void unavailableRoutingCreatesOnlyAnIdempotentPendingDistanceReview() {
        var trip = request(2, 1, 0, 1);
        when(routing.route(any())).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "Route provider unavailable"));
        var quoteFailure = assertThrows(PricingException.class, () -> trips.quote(trip));
        assertEquals("ROAD_DISTANCE_REVIEW_AVAILABLE", quoteFailure.getCode());
        String key = UUID.randomUUID().toString();
        var result = trips.requestDistanceReview(customer, trip, key);
        var id = (Long) result.get("bookingId");
        assertEquals("PENDING", result.get("bookingStatus"));
        assertEquals(id, trips.requestDistanceReview(customer, trip, key).get("bookingId"));
        assertTrue(jdbc.queryForObject("select road_distance_pending from booking where booking_id=?", Boolean.class, id));
        assertNull(jdbc.queryForObject("select booking_tripfare from booking where booking_id=?", java.math.BigDecimal.class, id));
        assertEquals(0, jdbc.queryForObject("select count(*) from driver_assignment", Integer.class));
        long adminId = jdbc.queryForObject("insert into admin(admin_name) values('Distance reviewer') returning admin_id", Long.class);
        assertEquals(new java.math.BigDecimal("160.00"),
                finance.confirmDistance(id, new java.math.BigDecimal("80"), adminId, pricing));
        assertFalse(jdbc.queryForObject("select road_distance_pending from booking where booking_id=?", Boolean.class, id));
        assertEquals(adminId, jdbc.queryForObject(
                "select road_distance_confirmed_by from booking where booking_id=?", Long.class, id));
    }

    @Test void capacityAndRoadFare() {
        var q=trips.quote(request(3,1,1,0)); assertTrue(q.available()); assertEquals(3,trips.luggageUnits(request(3,1,1,0)));
        assertEquals("Sedan",q.vehicle().get("vehicleType")); assertEquals(new java.math.BigDecimal("116.00"),q.totalFare()); assertEquals(58,q.estimatedDistanceKm());
    }
    @Test void categoryBootCapacityAndPassengerCapacityAreIndependent() {
        addVehicle("MPV", 2, 1);
        addVehicle("Premium MPV", 2, 1);
        addVehicle("10-Seater Van", 2, 1);
        addVehicle("18-Seater Van", 2, 1);
        jdbc.update("""
                update vehicle v
                set vehicle_category_id = c.vehicle_category_id
                from vehicle_category c where c.display_name = v.vehicle_type
                """);

        var mixed = trips.quote(request(4, 2, 1, 2));
        assertEquals(10, mixed.requiredBootUsageUnits());
        assertEquals("MPV", mixed.recommendedVehicleType());
        assertEquals(12, mixed.vehicle().get("bootCapacityUnits"));
        assertEquals("MPV", trips.quote(request(1, 0, 0, 4)).recommendedVehicleType());
        assertEquals("Sedan", trips.quote(request(4, 0, 0, 0)).recommendedVehicleType());
        assertEquals("Premium MPV", trips.quote(request(1, 0, 0, 5)).recommendedVehicleType());
        assertEquals("10-Seater Van", trips.quote(request(8, 2, 3, 3)).recommendedVehicleType());
        assertEquals("18-Seater Van", trips.quote(request(14, 4, 4, 4)).recommendedVehicleType());
    }
    @Test void exactPreferencePersistsAndAdminPreviewExplainsAnUnavailableDriver() throws Exception {
        long preferredDriver = addVehicle("Sedan", 4, 4);
        long preferredVehicle = jdbc.queryForObject("select vehicle_id from vehicle where driver_id=?", Long.class, preferredDriver);
        var base = request(3, 1, 1, 0);
        var exact = new TripQuoteRequest(base.pickupAddress(), base.destinationAddress(), base.pickupLatitude(),
                base.pickupLongitude(), base.destinationLatitude(), base.destinationLongitude(), base.scheduledAt(),
                3, 1, 1, 0, base.tripType(), null, null, "Sedan", null, null, null, preferredVehicle);
        var created = trips.create(customer, approved(exact), UUID.randomUUID().toString());
        long id = (Long) created.get("bookingId");
        assertEquals(preferredVehicle, jdbc.queryForObject("select preferred_vehicle_id from booking where booking_id=?", Long.class, id));
        assertEquals("Sedan", jdbc.queryForObject("select preferred_vehicle_category from booking where booking_id=?", String.class, id));
        assertNull(jdbc.queryForObject("select vehicle_id from booking where booking_id=?", Long.class, id));
        assertNull(jdbc.queryForObject("select driver_id from booking where booking_id=?", Long.class, id));
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("adminId", 1L);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/bookings/" + id + "/assignment-preview").session(session))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.preferred.driverId").value(preferredDriver))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.unavailableReason").doesNotExist());
        jdbc.update("update driver set driver_availability='UNAVAILABLE' where driver_id=?", preferredDriver);
        var preview = assignments.preview(id, null, null);
        assertTrue(preview.unavailableReason().contains("driver is not available"));
        assertEquals(vehicle, preview.alternatives().get(0).vehicleId());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> assignments.confirmAndAssign(new AssignmentRequest(id, preferredDriver, preferredVehicle, preview.scheduledEndAt()), null));
        assignments.changeSuggestedAssignment(new AssignmentRequest(id, driver, vehicle, preview.scheduledEndAt()));
        var confirmed = assignments.confirmAndAssign(new AssignmentRequest(id, driver, vehicle, preview.scheduledEndAt()), null);
        assertEquals("CONFIRMED", confirmed.bookingStatus());
        assertEquals(vehicle, confirmed.vehicleId());
        assertEquals(1, jdbc.queryForObject("select count(*) from notification where recipient_role='CUSTOMER' and notification_type='BOOKING_APPROVED'", Integer.class));
    }

    @Test void assignmentPreviewSeparatesRequestErrorsAndCalculatesExactEnd() throws Exception {
        long id = (Long) create().get("bookingId");
        jdbc.update("update booking set duration_seconds=832, reserved_end_at=null where booking_id=?", id);
        var path = "/api/bookings/" + id + "/assignment-preview";
        var admin = new org.springframework.mock.web.MockHttpSession();
        admin.setAttribute("adminId", 1L);
        var mobile = new org.springframework.mock.web.MockHttpSession();
        mobile.setAttribute("mobileRole", "CUSTOMER");
        mobile.setAttribute("mobileUserId", customer);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).session(mobile))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/bookings/999999/assignment-preview").session(admin))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value("Booking not found."));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .param("scheduledEndAt", "invalid").session(admin))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).session(admin))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.bookingId").value(id))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.preferredVehicleId").value(vehicle))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.driveDurationSeconds").value(832))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.operationalBufferSeconds").value(1800))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.estimatedTripEnd").value("2035-01-01T10:43:52"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.preferenceChecks.eligible").value(true));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> assignments.confirmAndAssign(new AssignmentRequest(id, driver, vehicle, start.plusMinutes(43)), null));
        var confirmed = assignments.confirmAndAssign(new AssignmentRequest(id, driver, vehicle, start.plusSeconds(2632)), null);
        assertEquals("CONFIRMED", confirmed.bookingStatus());
        assertEquals(start.plusSeconds(2632), confirmed.scheduledEndAt());
    }
    @Test void prePickupCheckFlagsMissingAndUnavailableAssignmentsOnce() {
        Long adminId = jdbc.queryForObject(
                "insert into admin(admin_name) values('Pickup monitor') returning admin_id", Long.class);
        long missing = (Long) create().get("bookingId");
        jdbc.update("update booking set booking_status='CONFIRMED' where booking_id=?", missing);
        long affected = (Long) create().get("bookingId");
        var assignment = assignments.confirmAndAssign(new AssignmentRequest(affected, driver, vehicle, start.plusHours(3)), null);
        assertEquals(0, pickupCheck.check(start.minusHours(2).minusSeconds(1)));
        jdbc.update("insert into vehicle_unavailable_period(vehicle_id,start_at,end_at) values(?,?,?)",
                vehicle, start.minusMinutes(15), start.plusHours(1));
        assertEquals(2, pickupCheck.check(start.minusHours(2)));
        assertEquals(0, pickupCheck.check(start.minusMinutes(110)));
        assertEquals(2, jdbc.queryForObject("select count(*) from booking_pickup_alert", Integer.class));
        assertTrue(jdbc.queryForObject("select reason from booking_pickup_alert where booking_id=?", String.class, affected)
                .contains("maintenance"));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from notification where recipient_role='ADMIN' and recipient_id=?
                  and notification_title='Booking awaiting assignment' and target_route=?
                """, Integer.class, adminId, "/bookings/" + missing));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from notification where recipient_role='ADMIN' and recipient_id=?
                  and notification_title='Pickup assignment requires review' and target_route=?
                """, Integer.class, adminId, "/bookings/" + affected));
        assertEquals(assignment.assignmentId(), assignments.getAssignment(assignment.assignmentId()).assignmentId());
    }

    @Test void differentCategoryRequiresExistingExplicitFareApprovalWhenCompanyUpgradesAreDisabled() {
        long id = (Long) create().get("bookingId");
        var initial = assignments.confirmAndAssign(new AssignmentRequest(id, driver, vehicle, start.plusHours(3)), null);
        long replacement = addVehicle("MPV", 6, 10);
        long replacementVehicle = jdbc.queryForObject("select vehicle_id from vehicle where driver_id=?", Long.class, replacement);
        long admin = jdbc.queryForObject("insert into admin(admin_name) values('Fare reviewer') returning admin_id", Long.class);
        var request = new AssignmentRequest(id, replacement, replacementVehicle, start.plusHours(3), "Maintenance");
        assertEquals(409, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> assignments.reassign(initial.assignmentId(), request)).getStatusCode().value());
        assertTrue(assignments.preview(id, null, initial.assignmentId()).alternatives().isEmpty());
        var proposal = bookingPrices.propose(id, "MPV", admin);
        bookingPrices.accept(id, (Long) proposal.get("proposalId"), customer);
        var updated = assignments.reassign(initial.assignmentId(), request);
        assertEquals(initial.bookingFare(), updated.bookingFare());
        assertEquals(initial.assignmentId(), updated.previousAssignmentId());
        assertEquals("Sedan", jdbc.queryForObject("select preferred_vehicle_category from booking where booking_id=?", String.class, id));
    }
    @Test void excessivePassengersExcluded() { assertFalse(trips.quote(request(5,0,0,0)).available()); }
    @Test void excessiveLuggageExcluded() { assertFalse(trips.quote(request(1,0,0,2)).available()); }
    @Test void maintenanceExcluded() { jdbc.update("update vehicle set vehicle_status='MAINTENANCE'");assertFalse(trips.quote(request(1,0,0,0)).available()); }
    @Test void maintenancePeriodExcluded() {
        jdbc.update("insert into vehicle_unavailable_period(vehicle_id,start_at,end_at) values(?,?,?)",vehicle,start.minusHours(1),start.plusHours(1));
        assertFalse(trips.quote(request(1,0,0,0)).available());
    }
    @Test void pendingRequestHoldsPairAndCancellationReleasesIt() {
        var b=create(); assertFalse(trips.quote(request(3,1,1,0)).available());
        bookings.cancelCustomerBooking(customer,((Number)b.get("bookingId")).longValue()); assertTrue(trips.quote(request(3,1,1,0)).available());
        create(); assertEquals(1,jdbc.queryForObject("select count(*) from booking_vehicle_hold",Integer.class));
        assertFalse(trips.quote(request(3,1,1,0)).available());
    }
    @Test void pendingAndRetryPersistExactlyOnce() {
        String key=UUID.randomUUID().toString();var accepted=approved(request(3,1,1,0));var first=trips.create(customer,accepted,key);
        assertEquals(first,trips.create(customer,accepted,key));
        assertEquals(1,jdbc.queryForObject("select count(*) from booking",Integer.class));
        assertEquals("PENDING",first.get("bookingStatus"));
        assertNull(jdbc.queryForObject("select driver_id from booking",Long.class));
        assertNull(jdbc.queryForObject("select vehicle_id from booking",Long.class));
        assertEquals(3,jdbc.queryForObject("select luggage_units from booking",Integer.class));
    }
    @Test void adminAssignmentChecksBothResourcesAndUpdatesBooking() {
        jdbc.update("update vehicle set vehicle_status='ACTIVE'");
        long id=((Number)create().get("bookingId")).longValue();
        assignments.confirmAndAssign(new AssignmentRequest(id,driver,null,start.plusHours(3)),null);
        assertEquals("CONFIRMED",jdbc.queryForObject("select booking_status from booking where booking_id=?",String.class,id));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from notification where driver_id=? and notification_status='UNREAD'
                """, Integer.class, driver));
        assertEquals(List.of("BOOKING_APPROVED"), jdbc.queryForList("""
                select notification_type from notification where recipient_role='CUSTOMER' and recipient_id=?
                and notification_type in ('BOOKING_APPROVED','DRIVER_ASSIGNED') and notification_status='UNREAD'
                """, String.class, customer));
        assertEquals(vehicle,jdbc.queryForObject("select vehicle_id from booking where booking_id=?",Long.class,id));
        assertFalse(trips.quote(request(3,1,1,0)).available());
    }
    @Test void rejectionStoresReasonAndReleases() {
        long id=((Number)create().get("bookingId")).longValue(); trips.reject(id,"Unavailable service");
        assertEquals("Unavailable service",jdbc.queryForObject("select rejection_reason from booking where booking_id=?",String.class,id));
        assertTrue(trips.quote(request(3,1,1,0)).available());
    }
    @Test void differentVehiclesAllowSameTimeAndSmallestAvailableTypeWins() {
        addVehicle("Van",10,16); create();
        assertEquals("Van",trips.quote(request(3,1,1,0)).vehicle().get("vehicleType")); create();
        assertEquals(2,jdbc.queryForObject("select count(*) from booking",Integer.class));
    }
    @Test void concurrentPendingRequestsDoNotReserveSameVehicle() throws Exception {
        var executor=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        var quote = approved(request(3,1,1,0));
        try {
            Callable<Boolean> task=()->{gate.await();try {trips.create(customer,quote,UUID.randomUUID().toString());return true;}catch(org.springframework.web.server.ResponseStatusException e){assertEquals(409,e.getStatusCode().value());return false;}};
            var a=executor.submit(task);var b=executor.submit(task);gate.countDown();
            assertNotEquals(a.get(20,TimeUnit.SECONDS), b.get(20,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("select count(*) from booking",Integer.class));
            assertEquals(1,jdbc.queryForObject("select count(*) from booking_vehicle_hold",Integer.class));
        } finally {executor.shutdownNow();}
    }

    @Test void concurrentSubmissionsCanUseTwoFreePairsInTheSelectedCategory() throws Exception {
        addVehicle("SEDAN", 4, 5);
        var accepted = approved(request(3,1,1,0));
        var otherCustomer = jdbc.queryForObject("""
                insert into customer(cust_name,cust_phoneno,cust_status)
                values('Second Customer','0199999999','ACTIVE') returning cust_id
                """, Long.class);
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { gate.await(); return trips.create(customer, accepted, "paired-first"); });
            var b = pool.submit(() -> { gate.await(); return trips.create(otherCustomer, accepted, "paired-second"); });
            gate.countDown();
            assertEquals("PENDING", a.get(20, TimeUnit.SECONDS).get("bookingStatus"));
            assertEquals("PENDING", b.get(20, TimeUnit.SECONDS).get("bookingStatus"));
            assertEquals(2, jdbc.queryForObject("select count(distinct driver_id) from booking_vehicle_hold", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test void routeFailureCannotCreateBooking() {
        when(routing.route(any())).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Routing failed"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,this::create);
        assertEquals(0,jdbc.queryForObject("select count(*) from booking",Integer.class));
    }
    @Test void changedTimeRefreshesAvailabilityAndFare() {
        create();start=start.plusHours(4);assertTrue(trips.quote(request(3,1,1,0)).available());
        verify(routing,times(3)).route(any());
    }
    @Test void editRefreshesSavedFareScheduleAndHold() {
        long id=((Number)create().get("bookingId")).longValue();
        assertTrue(trips.quoteEdit(customer,id,request(3,1,1,0)).available());
        start=start.plusDays(1);
        when(routing.route(any())).thenReturn(new RoadRoutingService.Route(70,9000,List.of(List.of(101.0,3.0),List.of(102.0,4.0))));
        trips.update(customer,id,approved(request(3,1,1,0)));
        assertEquals(new java.math.BigDecimal("140.00"),jdbc.queryForObject("select booking_tripfare from booking where booking_id=?",java.math.BigDecimal.class,id));
        assertEquals(start.plusSeconds(9000).plusMinutes(30),jdbc.queryForObject("select reserved_end_at from booking where booking_id=?",LocalDateTime.class,id));
        assertEquals(start,jdbc.queryForObject("select start_at from booking_vehicle_hold",LocalDateTime.class));
        assertEquals(1,jdbc.queryForObject("select count(*) from booking",Integer.class));
    }
    @Test void pendingAndRejectedCustomersCannotCreateThroughAnyCustomerRoute() throws Exception {
        for (String status : List.of("PENDING", "REJECTED")) {
            jdbc.update("update customer set cust_status=? where cust_id=?", status, customer);
            for (String path : List.of("/api/bookings/customer", "/api/bookings/customer/complete",
                    "/api/bookings/customer/road-distance-review")) {
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                                .sessionAttr("mobileUserId", customer)
                                .sessionAttr("mobileRole", "CUSTOMER")
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType("application/json")
                                .content(json.writeValueAsString(request(3,1,1,0))))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
            }
        }
        assertEquals(0,jdbc.queryForObject("select count(*) from booking",Integer.class));
    }
    @Test void twoCustomersCanRequestSameSlotThenAdminConfirmsAlternativeAndHistoryIsPreserved() throws Exception {
        long first=((Number)create().get("bookingId")).longValue();
        long otherCustomer=jdbc.queryForObject("insert into customer(cust_name,cust_phoneno,cust_status) values('Second Customer','0199999999','ACTIVE') returning cust_id",Long.class);
        long alternative = addVehicle("Sedan",8,10);
        long alternativeVehicle = jdbc.queryForObject("select vehicle_id from vehicle where driver_id=?",Long.class,alternative);
        long second=((Number)trips.create(otherCustomer,approved(request(3,1,1,0)),UUID.randomUUID().toString()).get("bookingId")).longValue();
        assertEquals(2,jdbc.queryForObject("select count(*) from booking where booking_status='PENDING'",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from driver_assignment",Integer.class));
        assertEquals(2,jdbc.queryForObject("select count(*) from booking_vehicle_hold",Integer.class));
        var admin=new org.springframework.mock.web.MockHttpSession(); admin.setAttribute("adminId",1L);
        assertTrue(assignments.getDriverAssignments(driver).isEmpty());
        var body=Map.of("driverId",driver,"vehicleId",vehicle,"scheduledEndAt",start.plusHours(3).toString());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/bookings/"+first+"/assignment-options").session(admin))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].vehicleId").value(vehicle));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bookings/"+first+"/confirm-and-assign").session(admin)
            .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bookings/"+second+"/confirm-and-assign").session(admin)
            .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        assertEquals("PENDING",jdbc.queryForObject("select booking_status from booking where booking_id=?",String.class,second));
        assertEquals(1,jdbc.queryForObject("select count(*) from driver_assignment",Integer.class));
        assertEquals(alternativeVehicle, assignments.options(second,null).get(0).vehicleId());
        assignments.confirmAndAssign(new AssignmentRequest(second,alternative,alternativeVehicle,start.plusHours(3)),null);
        var adminList=assignments.getAllAssignments(); assertEquals(2,adminList.size());
        assertEquals(1,assignments.getDriverAssignments(driver).size());
        var customerView = bookings.getCustomerBookings(otherCustomer).get(0);
        assertEquals("CONFIRMED",customerView.status());
        assertNotNull(customerView.assignment());
        assertEquals("QA" + alternative, customerView.assignment().vehiclePlate());
        assertEquals(3,assignments.getDriverAssignments(driver).get(0).luggageUnits());
        long secondAssignment=adminList.stream().filter(a->a.bookingId().equals(second)).findFirst().orElseThrow().assignmentId();
        assignments.deleteAssignment(secondAssignment);
        assertEquals("CANCELLED",jdbc.queryForObject("select booking_status from booking where booking_id=?",String.class,second));
        long firstAssignment=adminList.stream().filter(a->a.bookingId().equals(first)).findFirst().orElseThrow().assignmentId();
        var replacement=assignments.reassign(firstAssignment,new AssignmentRequest(first,alternative,alternativeVehicle,start.plusHours(3),"Vehicle maintenance"));
        assertEquals(firstAssignment,replacement.previousAssignmentId());
        assertEquals("Vehicle maintenance",replacement.reassignmentReason());
        assertNotEquals(firstAssignment,replacement.assignmentId());
        assertEquals("SUPERSEDED",jdbc.queryForObject("select assignment_status from driver_assignment where assignment_id=?",String.class,firstAssignment));
        assertEquals(driver,jdbc.queryForObject("select driver_id from driver_assignment where assignment_id=?",Long.class,firstAssignment));
        assertEquals(3,jdbc.queryForObject("select count(*) from driver_assignment",Integer.class));
        assertEquals("QA" + alternative, bookings.getCustomerBookings(customer).get(0).assignment().vehiclePlate());
        assertTrue(assignments.getDriverAssignments(driver).isEmpty());
        assertEquals(2,assignments.getDriverAssignments(alternative).size());
        assertTrue(trips.quote(request(3,1,1,0)).available()); // original smaller vehicle released
        assertTrue(jdbc.queryForObject("select count(*) from notification where driver_id=?",Integer.class,alternative)>0);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/assignments").session(admin))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(3));
        var mobile=new org.springframework.mock.web.MockHttpSession(); mobile.setAttribute("mobileRole","DRIVER");mobile.setAttribute("mobileUserId",alternative);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/assignments/mine").session(mobile))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].assignmentId").value(replacement.assignmentId()));
    }
    @Test void concurrentAdminConfirmationsAreSerializedAndOnlyOneWins() throws Exception {
        long first=((Number)create().get("bookingId")).longValue();
        long second=first;
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            java.util.function.Function<Long,Callable<Boolean>> confirm=id->()->{gate.await();try {
                assignments.confirmAndAssign(new AssignmentRequest(id,driver,vehicle,start.plusHours(3)),null);return true;
            } catch(org.springframework.web.server.ResponseStatusException e) {assertEquals(409,e.getStatusCode().value());return false;}};
            var a=pool.submit(confirm.apply(first));var b=pool.submit(confirm.apply(second));gate.countDown();
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("select count(*) from driver_assignment",Integer.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from booking where booking_status='PENDING'",Integer.class));
        } finally {pool.shutdownNow();}
    }
    @Test void ancientHoldsAndPendingAssignmentRowsDoNotOccupyAvailability() {
        long id=((Number)create().get("bookingId")).longValue();
        jdbc.update("update booking_vehicle_hold set driver_id=null, expires_at=? where booking_id=?",start.plusDays(1),id);
        jdbc.update("insert into driver_assignment(booking_id,driver_id,vehicle_id,assignment_date,assignment_status,scheduled_end_at) values(?,?,?,localtimestamp,'ASSIGNED',?)",id,driver,vehicle,start.plusHours(3));
        assertTrue(trips.quote(request(3,1,1,0)).available());
        assertTrue(assignments.getDriverAssignments(driver).isEmpty());
    }
    @Test void driverUnavailableIsExcludedEvenWhenVehicleFits() {
        long id=((Number)create().get("bookingId")).longValue();
        jdbc.update("insert into driver_unavailable_period(driver_id,start_at,end_at,reason) values(?,?,?,'Leave')",driver,start.minusMinutes(1),start.plusHours(3));
        assertTrue(assignments.options(id,null).isEmpty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->assignments.confirmAndAssign(new AssignmentRequest(id,driver,vehicle,start.plusHours(3)),null));
        assertEquals(0,jdbc.queryForObject("select count(*) from driver_assignment",Integer.class));
    }
    @Test void midnightOverlapAdjacentTripsAndCompletionRelease() {
        start=start.withHour(23);long id=((Number)create().get("bookingId")).longValue();
        var assigned=assignments.confirmAndAssign(new AssignmentRequest(id,driver,vehicle,start.plusHours(3)),null);
        start=start.plusHours(2);assertFalse(trips.quote(request(3,1,1,0)).available());
        start=start.plusHours(1);assertTrue(trips.quote(request(3,1,1,0)).available()); // touching interval is not overlap
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> assignments.completeAssignment(assigned.assignmentId()));
        for (String step : List.of("OTW", "WAITING", "PICKUP", "DROP", "COMPLETED")) {
            assignments.updateDriverStatus(assigned.assignmentId(), driver, step);
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from payment", Integer.class));
        assertEquals("COMPLETED", bookings.getCustomerBookings(customer).get(0).status());
        start=start.minusHours(1);assertFalse(trips.quote(request(3,1,1,0)).available()); // completed history retains its occupied interval
        start=start.plusHours(2);assertTrue(trips.quote(request(3,1,1,0)).available());
        assertEquals("COMPLETED",jdbc.queryForObject("select assignment_status from driver_assignment",String.class));
    }
    @Test void recommendationIsIndependentOfPricingAndDoesNotAssign() {
        addVehicle("Cheaper",4,4);
        jdbc.update("update booking_fare_rate set rate_per_km=1 where vehicle_type='Cheaper'");
        assertEquals("Sedan",trips.availability(request(3,1,1,0),null).vehicleModel());
        assertEquals(0,jdbc.queryForObject("select count(*) from driver_assignment",Integer.class));
    }
    @Test void structuredAvailabilityDistinguishesCapacityScheduleAndConfiguration() {
        var available=trips.quote(request(3,1,1,0));
        assertEquals(CustomerTripService.AvailabilityStatus.AVAILABLE,available.availabilityStatus());
        assertEquals(1,available.suitableVehicleCount());assertEquals(3,available.requiredLuggagePoints());
        assertEquals(start.plusHours(2).plusMinutes(30),available.occupiedEnd());
        assertEquals(CustomerTripService.AvailabilityStatus.PASSENGER_CAPACITY_UNAVAILABLE,trips.quote(request(5,0,0,0)).availabilityStatus());
        assertEquals(CustomerTripService.AvailabilityStatus.LUGGAGE_CAPACITY_UNAVAILABLE,trips.quote(request(1,0,0,2)).availabilityStatus());
        jdbc.update("update vehicle set vehicle_status='MAINTENANCE'");
        assertEquals(CustomerTripService.AvailabilityStatus.SCHEDULE_UNAVAILABLE,trips.quote(request(3,1,1,0)).availabilityStatus());
        jdbc.update("update vehicle set vehicle_luggagecapacity=null");
        assertEquals(CustomerTripService.AvailabilityStatus.FLEET_NOT_CONFIGURED,trips.quote(request(3,1,1,0)).availabilityStatus());
    }
    @Test void unknownCompletedLegacyEndDoesNotPermanentlyBlockFutureTrips() {
        long id=((Number)create().get("bookingId")).longValue();
        jdbc.update("update booking set booking_status='COMPLETED',booking_tripdate=?,reserved_end_at=null where booking_id=?",start.minusDays(1).toLocalDate(),id);
        jdbc.update("insert into driver_assignment(booking_id,driver_id,vehicle_id,assignment_date,assignment_status,scheduled_end_at) values(?,?,?,localtimestamp,'COMPLETED',null)",id,driver,vehicle);
        assertTrue(trips.quote(request(3,1,1,0)).available());
    }
    @Test void submissionRechecksAfterPreviouslySuccessfulAvailability() {
        assertTrue(trips.quote(request(3,1,1,0)).available());
        jdbc.update("insert into vehicle_unavailable_period(vehicle_id,start_at,end_at) values(?,?,?)",vehicle,start,start.plusHours(3));
        var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,this::create);
        assertEquals(409,error.getStatusCode().value());
        assertEquals(0,jdbc.queryForObject("select count(*) from booking",Integer.class));
    }


    @Test void recordsMilestonesAndIncidentsThroughAuthorizedEndpoints() throws Exception {
        long booking = (Long) create().get("bookingId");
        long id = assignments.confirmAndAssign(new AssignmentRequest(booking, driver, vehicle, start.plusHours(3)), null).assignmentId();
        var owner = mobileSession(driver, "DRIVER");
        var anotherDriver = mobileSession(addVehicle("MPV", 5, 8), "DRIVER");
        var customerSession = mobileSession(customer, "CUSTOMER");
        var admin = new org.springframework.mock.web.MockHttpSession();
        admin.setAttribute("adminId", 1L);
        String base = "/api/assignments/" + id;
        mvc.perform(get(base + "/timeline").session(anotherDriver)).andExpect(status().isNotFound());
        mvc.perform(patch(base + "/status").session(owner).contentType("application/json").content("{\"status\":\"PICKUP\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch(base + "/status").session(owner).contentType("application/json").content("{\"status\":\"OTW\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch(base + "/status").session(owner).contentType("application/json").content("{\"status\":\"OTW\"}"))
                .andExpect(status().isConflict());
        assertEquals(2, jdbc.queryForObject("select count(*) from trip_milestone_event where assignment_id=? and occurred_at is not null", Integer.class, id));
        mvc.perform(get("/api/bookings/" + booking + "/timeline").session(customerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$[1].milestone").value("OTW"))
                .andExpect(jsonPath("$[1].occurredAt").isNotEmpty());
        String report = "{\"category\":\"DELAY\",\"description\":\"Road blocked near pickup\"}";
        mvc.perform(post(base + "/incidents").session(anotherDriver).contentType("application/json").content(report))
                .andExpect(status().isNotFound());
        mvc.perform(post(base + "/incidents").session(owner).contentType("application/json").content(report))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.incidentId").isNumber());
        mvc.perform(get("/api/assignments/incidents").session(owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/assignments/incidents").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Road blocked near pickup"));
        mvc.perform(get("/api/dashboard/operations").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.today_trips").isNumber())
                .andExpect(jsonPath("$.active_trips[0].milestone").value("OTW"));
    }

    @Test void verifiedPaymentsAloneCoverInvoiceAndCountInReceipts() throws Exception {
        long booking = (Long) create().get("bookingId");
        var admin = hardeningAdmin("Receipt reviewer", "ADMIN");
        Long invoice = jdbc.queryForObject("""
                insert into documents(document_number,document_type,booking_id,cust_id,cust_name,amount,status)
                values('HARD-INV','INVOICE',?,?,'Fixture customer',100,'ISSUED') returning document_id
                """, Long.class, booking, customer);
        for (String state : List.of("VERIFIED", "PENDING_VERIFICATION", "REJECTED")) {
            Long payment = jdbc.queryForObject("""
                    insert into payment(booking_id,amount,payment_method,payment_status)
                    values(?,60,'CASH',?) returning payment_id
                    """, Long.class, booking, state);
            jdbc.update("""
                    insert into documents(document_number,document_type,booking_id,cust_id,cust_name,payment_id,amount,status)
                    values(?,'RECEIPT',?,?,'Fixture customer',?,60,'PAID')
                    """, "HARD-" + payment, booking, customer, payment);
        }
        mvc.perform(get("/api/documents/dashboard").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceiptAmount").value(60));
        jdbc.update("update payment set verified_at=current_timestamp where booking_id=?", booking);
        mvc.perform(get("/api/dashboard/operations").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.verified_payments_month").value(60))
                .andExpect(jsonPath("$.issued_invoice_balance").value(40))
                .andExpect(jsonPath("$.payments_to_review").value(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/documents/" + invoice + "/mark-paid").session(admin)).andExpect(status().isConflict());
        jdbc.update("insert into payment(booking_id,amount,payment_method,payment_status) values(?,40,'CASH','VERIFIED')", booking);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/documents/" + invoice + "/mark-paid").session(admin)).andExpect(status().isOk());
    }

    @Test void adminCanManageUnavailablePeriodsAndReceivesConflictingBookingIds() throws Exception {
        var admin = hardeningAdmin("Schedule reviewer", "ADMIN");
        String path = "/api/admin/unavailability/VEHICLE/" + vehicle;
        String overlapping = periodJson(start.minusMinutes(5), start.plusHours(1));
        mvc.perform(post(path).session(mobileSession(customer, "CUSTOMER")).contentType("application/json").content(overlapping))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).session(admin).contentType("application/json").content(periodJson(start, start.plusDays(32))))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).session(admin).contentType("application/json").content(periodJson(start, start)))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).session(admin).contentType("application/json").content(periodJson(start.minusDays(2), start.minusDays(1))))
                .andExpect(status().isCreated());
        mvc.perform(get(path).session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").isNumber());
        Long period = jdbc.queryForObject("select id from vehicle_unavailable_period where vehicle_id=?", Long.class, vehicle);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path + "/" + period).session(admin))
                .andExpect(status().isNoContent());
        mvc.perform(get(path).session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        long booking = (Long) create().get("bookingId");
        assignments.confirmAndAssign(new AssignmentRequest(booking, driver, vehicle, start.plusHours(3)), null);
        mvc.perform(post(path).session(admin).contentType("application/json").content(overlapping))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.bookingIds[0]").value(booking));
    }

    private String periodJson(LocalDateTime from, LocalDateTime to) {
        return """
                {"startAt":"%s","endAt":"%s","reason":"Planned downtime"}
                """.formatted(from, to);
    }

    @Test void periodCreationAndConfirmationCannotBothWinForTheSameDriver() throws Exception {
        long booking = (Long) create().get("bookingId");
        var ready = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var block = new com.diladrive.service.ResourceUnavailabilityService.Input(start.minusMinutes(5), start.plusHours(1), "Planned downtime");
        try {
            var period = pool.submit(() -> {
                ready.await();
                try {
                    periods.create(com.diladrive.service.ResourceUnavailabilityService.Resource.DRIVER, driver, block);
                    return 201;
                } catch (org.springframework.web.server.ResponseStatusException error) {
                    return error.getStatusCode().value();
                }
            });
            var assignment = pool.submit(() -> {
                ready.await();
                try {
                    assignments.confirmAndAssign(new AssignmentRequest(booking, driver, vehicle, start.plusHours(3)), null);
                    return 200;
                } catch (org.springframework.web.server.ResponseStatusException error) {
                    return error.getStatusCode().value();
                }
            });
            ready.countDown();
            int periodStatus = period.get(10, java.util.concurrent.TimeUnit.SECONDS);
            int assignmentStatus = assignment.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue((periodStatus == 201 && assignmentStatus == 409)
                    || (periodStatus == 409 && assignmentStatus == 200), periodStatus + " / " + assignmentStatus);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void bothCustomerTypesCanDownloadOnlyTheirVerifiedReceipts() throws Exception {
        Long corporate = jdbc.queryForObject(
                "insert into customer(cust_name,cust_status,cust_type) values('Test Company','ACTIVE','COMPANY') returning cust_id", Long.class);
        jdbc.update("update customer set cust_type='NORMAL' where cust_id=?", customer);
        for (Long owner : List.of(customer, corporate)) {
            Long booking = jdbc.queryForObject(
                    "insert into booking(cust_id,booking_status,booking_tripfare) values(?,'COMPLETED',60) returning booking_id", Long.class, owner);
            Long receipt = receiptFixture(owner, booking, "VERIFIED");
            Long rejected = receiptFixture(owner, booking, "REJECTED");
            var session = mobileSession(owner, "CUSTOMER");
            var stranger = mobileSession(owner.equals(customer) ? corporate : customer, "CUSTOMER");
            mvc.perform(get("/api/documents/customer/receipts").session(session)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].documentId").value(receipt));
            mvc.perform(get("/api/documents/customer/receipts/" + receipt + "/pdf").session(session))
                    .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/pdf"));
            mvc.perform(get("/api/documents/customer/receipts/" + rejected + "/pdf").session(session)).andExpect(status().isNotFound());
            mvc.perform(get("/api/documents/customer/receipts/" + receipt + "/pdf").session(stranger)).andExpect(status().isNotFound());
        }
    }

    private Long receiptFixture(Long owner, Long booking, String state) {
        Long payment = jdbc.queryForObject(
                "insert into payment(booking_id,amount,payment_method,payment_status) values(?,60,'CASH',?) returning payment_id",
                Long.class, booking, state);
        return jdbc.queryForObject("""
                insert into documents(document_number,document_type,booking_id,cust_id,cust_name,payment_id,amount,status)
                values(?,'RECEIPT',?,?,'Test customer',?,60,'PAID') returning document_id
                """, Long.class, "TEST-REC-" + payment, booking, owner, payment);
    }

    @Test void quotationApprovalNotifiesOnceAndOnlyClientPurchaseOrdersCanBeRecorded() throws Exception {
        long booking = (Long) create().get("bookingId");
        var admin = hardeningAdmin("Quotation reviewer", "ADMIN");
        jdbc.update("update customer set cust_type='COMPANY',cust_companyname='Test Company',company_registration_number='TEST123' where cust_id=?", customer);
        Long quote = jdbc.queryForObject("""
                insert into documents(document_number,document_type,booking_id,cust_id,cust_name,amount,status)
                values('TEST-QUO','QUOTATION',?,?,'Test Company',60,'REJECTED') returning document_id
                """, Long.class, booking, customer);
        String base = "/api/documents/" + quote;
        String edit = "{\"bookingId\":" + booking + ",\"amount\":60}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(base)
                    .session(admin).contentType("application/json").content(edit)).andExpect(status().isOk());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(base + "/approve").session(admin)).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(base + "/approve").session(admin)).andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(base)
                .session(admin).contentType("application/json").content(edit)).andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("select count(*) from notification where recipient_id=? and recipient_role='CUSTOMER' and notification_type='QUOTATION_APPROVED'", Integer.class, customer));
        mvc.perform(post(base + "/generate-po").session(admin)).andExpect(status().isGone());
        mvc.perform(post(base + "/record-po").session(admin).contentType("application/json").content("{\"clientReference\":\"CUSTOMER-PO-001\"}"))
                .andExpect(status().isCreated());
    }

    @Test void reassignmentTripProgressAndBothReminderWindowsPersistExactlyOnce() {
        long booking = (Long) create().get("bookingId");
        var admin = hardeningAdmin("Reminder reviewer", "ADMIN");
        var first = assignments.confirmAndAssign(new AssignmentRequest(booking, driver, vehicle, start.plusHours(3)), null);
        Long replacement = addVehicle("Sedan", 4, 4);
        Long newVehicle = jdbc.queryForObject("select vehicle_id from vehicle where driver_id=?", Long.class, replacement);
        var next = assignments.reassign(first.assignmentId(),
                new AssignmentRequest(booking, replacement, newVehicle, start.plusHours(3), "Driver unavailable"));
        assignments.updateStatus(next.assignmentId(), "OTW");
        assignments.updateStatus(next.assignmentId(), "OTW");
        for (String type : List.of("ASSIGNMENT_REASSIGNED", "TRIP_STATUS")) {
            assertEquals(1, jdbc.queryForObject("""
                    select count(*) from notification where recipient_role='ADMIN' and recipient_id=?
                      and notification_type=? and target_route=?
                    """, Integer.class, admin.getAttribute("adminId"), type, "/assignments/" + next.assignmentId()));
        }
        tripReminders.sendUpcomingReminders(start.minusHours(24));
        tripReminders.sendUpcomingReminders(start.minusHours(24));
        tripReminders.sendUpcomingReminders(start.minusHours(2));
        tripReminders.sendUpcomingReminders(start.minusHours(2));
        for (String type : List.of("TRIP_REMINDER_24H", "TRIP_REMINDER_2H")) {
            assertEquals(1, jdbc.queryForObject("select count(*) from notification where recipient_role='CUSTOMER' and recipient_id=? and notification_type=?", Integer.class, customer, type));
            assertEquals(1, jdbc.queryForObject("select count(*) from notification where recipient_role='DRIVER' and recipient_id=? and notification_type=?", Integer.class, replacement, type));
        }
    }

    @Test void lastSuperAdminIsProtectedDuringConcurrentRemoval() throws Exception {
        var left = hardeningAdmin("Concurrent left", "SUPER_ADMIN");
        var right = hardeningAdmin("Concurrent right", "SUPER_ADMIN");
        // Other integration cases may have created admins. Only these two are super admins for this test.
        jdbc.update("update admin set admin_role='ADMIN' where admin_role='SUPER_ADMIN' and admin_id not in (?,?)",
                left.getAttribute("adminId"), right.getAttribute("adminId"));
        var startBoth = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { startBoth.await(); return updateHardeningAdmin(left, left, "ADMIN", "ACTIVE"); });
            var second = pool.submit(() -> { startBoth.await(); return updateHardeningAdmin(right, right, "SUPER_ADMIN", "INACTIVE"); });
            startBoth.countDown();
            var results = new java.util.ArrayList<>(List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS)));
            java.util.Collections.sort(results);
            assertEquals(List.of(200, 409), results);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from admin where admin_role='SUPER_ADMIN' and admin_status='ACTIVE'", Integer.class));
        var survivor = "SUPER_ADMIN".equals(jdbc.queryForObject("select admin_role from admin where admin_id=?", String.class,
                left.getAttribute("adminId"))) ? left : right;
        var other = hardeningAdmin("Other account", "ADMIN");
        assertEquals(403, updateHardeningAdmin(other, survivor, "ADMIN", "ACTIVE"));
        assertEquals(409, updateHardeningAdmin(survivor, survivor, "ADMIN", "ACTIVE"));
        assertEquals(409, updateHardeningAdmin(survivor, survivor, "SUPER_ADMIN", "INACTIVE"));
        assertEquals(200, updateHardeningAdmin(survivor, other, "ADMIN", "INACTIVE"));
        mvc.perform(get("/api/dashboard/stats").session(other)).andExpect(status().isUnauthorized());
    }

    private org.springframework.mock.web.MockHttpSession hardeningAdmin(String name, String role) {
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("adminId", jdbc.queryForObject(
                "insert into admin(admin_name,admin_role) values(?,?) returning admin_id", Long.class, name, role));
        return session;
    }

    private int updateHardeningAdmin(org.springframework.mock.web.MockHttpSession actor,
            org.springframework.mock.web.MockHttpSession target, String role, String state) throws Exception {
        Object id = target.getAttribute("adminId");
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admins/" + id)
                .session(actor).contentType("application/json").content("""
                {"name":"Test Admin","email":"hardening-%s@example.test","phone":"0123456789",
                 "address":"Test address","role":"%s","status":"%s"}
                """.formatted(id, role, state))).andReturn().getResponse().getStatus();
    }

    @Test void driverCreationValidatesCapacityAndRollsBackInvalidUploads() throws Exception {
        var admin = new org.springframework.mock.web.MockHttpSession();
        admin.setAttribute("adminId", jdbc.queryForObject(
                "insert into admin(admin_name) values('Driver reviewer') returning admin_id", Long.class));
        var payload = json.readTree("""
                {"driverName":"New Test Driver","driverEmail":"new-driver@example.test",
                 "driverPassword":"Example123","driverPhoneno":"0123456778","driverIcno":"900101011234",
                 "driverLicenseno":"TEST-LIC","driverLicenseExpiry":"2035-01-01","driverType":"Company Driver",
                 "vehicle":{"vehicleModel":"QA MPV","vehiclePlateno":"QA-NEW","vehicleType":"MPV","vehicleCapacity":6,
                   "vehicleLuggageCapacity":12,"vehicleOwnership":"DRIVER_OWNED","vehicleStatus":"AVAILABLE",
                   "vehicleRoadtaxexpiry":"2035-12-31","vehicleServiceexpiry":"2035-12-31"}}
                """);
        var vehicleData = (com.fasterxml.jackson.databind.node.ObjectNode) payload.get("vehicle");
        vehicleData.put("vehicleCategoryId", jdbc.queryForObject(
                "select vehicle_category_id from vehicle_category where category_key='MPV'", Long.class));
        var buffer = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", buffer);
        byte[] png = buffer.toByteArray();
        long originalFiles;
        try (var files = Files.list(fleetStorage)) { originalFiles = files.count(); }

        for (String field : List.of("vehicleCapacity", "vehicleLuggageCapacity")) {
            int value = vehicleData.remove(field).asInt();
            mvc.perform(driverRegistration(admin, payload, png, png)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors['vehicle." + field + "'][0]").exists());
            vehicleData.put(field, value);
        }
        for (int invalidCapacity : new int[]{0, 5}) {
            vehicleData.put("vehicleCapacity", invalidCapacity);
            mvc.perform(driverRegistration(admin, payload, png, png)).andExpect(status().isBadRequest());
        }
        vehicleData.put("vehicleCapacity", 6.5);
        mvc.perform(driverRegistration(admin, payload, png, png)).andExpect(status().isBadRequest());
        vehicleData.put("vehicleCapacity", 6);
        mvc.perform(driverRegistration(admin, payload, png, new byte[] {0, 1, 2})).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("select count(*) from driver where driver_email='new-driver@example.test'", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from vehicle where vehicle_plateno='QA-NEW'", Integer.class));
        try (var files = Files.list(fleetStorage)) { assertEquals(originalFiles, files.count()); }

        mvc.perform(driverRegistration(admin, payload, png, png)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicle.vehicleCapacity").value(6)).andExpect(jsonPath("$.vehicle.bootCapacityUnits").value(12));
        Long id = jdbc.queryForObject("select driver_id from driver where driver_email='new-driver@example.test'", Long.class);
        Long vehicleId = jdbc.queryForObject("select vehicle_id from vehicle where driver_id=?", Long.class, id);
        mvc.perform(driverRegistration(admin, payload, png, png)).andExpect(status().is4xxClientError());
        assertEquals(1, jdbc.queryForObject("select count(*) from vehicle where driver_id=?", Integer.class, id));
        mvc.perform(get("/api/drivers/" + id).session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.licenseDocument").exists());
        mvc.perform(get("/api/vehicles/" + vehicleId).session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.vehicleLuggageCapacity").value(12));
        vehicleData.put("vehicleModel", "Updated QA MPV");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/vehicles/" + vehicleId)
                .session(admin).contentType("application/json").content(json.writeValueAsBytes(vehicleData)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vehicleModel").value("Updated QA MPV"));
        assertTrue(jdbc.queryForObject("select driver_icno is null and driver_icno_ciphertext is not null from driver where driver_id=?", Boolean.class, id));
        var withoutIc = (com.fasterxml.jackson.databind.node.ObjectNode) payload;
        withoutIc.remove("driverIcno");
        withoutIc.put("driverEmail", "no-ic-driver@example.test");
        vehicleData.put("vehiclePlateno", "QA-NO-IC");
        mvc.perform(driverRegistration(admin, withoutIc, png, png, false)).andExpect(status().isCreated());
        assertTrue(jdbc.queryForObject("select driver_icno_ciphertext is null from driver where driver_email='no-ic-driver@example.test'", Boolean.class));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder driverRegistration(
            org.springframework.mock.web.MockHttpSession admin, com.fasterxml.jackson.databind.JsonNode payload,
            byte[] png, byte[] roadTax) throws Exception {
        return driverRegistration(admin, payload, png, roadTax, true);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder driverRegistration(
            org.springframework.mock.web.MockHttpSession admin, com.fasterxml.jackson.databind.JsonNode payload,
            byte[] png, byte[] roadTax, boolean includeIc) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/drivers")
                .file(new org.springframework.mock.web.MockMultipartFile("driver", "driver.json", "application/json", json.writeValueAsBytes(payload)))
                .file(new org.springframework.mock.web.MockMultipartFile("licenseDocument", "licence.png", "image/png", png))
                .file(new org.springframework.mock.web.MockMultipartFile("roadTaxDocument", "road-tax.png", "image/png", roadTax));
        if (includeIc) request.file(new org.springframework.mock.web.MockMultipartFile("icDocument", "ic.png", "image/png", png));
        return request.session(admin);
    }

    @Test void adminInboxLinksNewAccountsAndBookingsAndKeepsIndependentReadState() throws Exception {
        var first = new org.springframework.mock.web.MockHttpSession();
        var second = new org.springframework.mock.web.MockHttpSession();
        Long firstId = jdbc.queryForObject(
                "insert into admin(admin_name,admin_status) values('Inbox first','ACTIVE') returning admin_id", Long.class);
        Long secondId = jdbc.queryForObject(
                "insert into admin(admin_name,admin_status) values('Inbox second','ACTIVE') returning admin_id", Long.class);
        first.setAttribute("adminId", firstId);
        second.setAttribute("adminId", secondId);
        mvc.perform(post("/api/auth/mobile/register").contentType("application/json").content("""
                {"name":"Inbox customer","email":"inbox@example.test","phone":"0123456778",
                 "type":"NORMAL","password":"Example123","confirmPassword":"Example123"}
                """)).andExpect(status().isCreated());
        Long registeredId = jdbc.queryForObject(
                "select cust_id from customer where cust_email='inbox@example.test'", Long.class);
        var customerSession = mobileSession(customer, "CUSTOMER");
        var body = json.writeValueAsString(approved(request(3, 1, 1, 0)));
        for (int retry = 0; retry < 2; retry++) {
            mvc.perform(post("/api/bookings/customer/complete").session(customerSession)
                    .header("Idempotency-Key", "notification-integration").contentType("application/json").content(body))
                    .andExpect(status().isCreated());
        }
        Long bookingId = jdbc.queryForObject("select booking_id from booking", Long.class);
        inboxNotifications.notifyAdminsOfNewBooking(bookingId);
        mvc.perform(get("/api/notifications/inbox").session(first).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].targetRoute").value("/bookings/" + bookingId));
        mvc.perform(get("/api/notifications/inbox").session(first).param("type", "CUSTOMER_REGISTRATION")
                        .param("search", "awaiting verification").param("status", "UNREAD"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].targetRoute").value("/customers/" + registeredId));
        Long itemId = jdbc.queryForObject("""
                select notification_id from notification
                where recipient_role='ADMIN' and recipient_id=? and notification_type='BOOKING'
                """, Long.class, firstId);
        mvc.perform(patch("/api/notifications/" + itemId + "/read").session(second))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/notifications/" + itemId + "/read").session(first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").isNotEmpty());
        var originalReadAt = jdbc.queryForObject("select read_at from notification where notification_id=?",
                LocalDateTime.class, itemId);
        mvc.perform(patch("/api/notifications/" + itemId + "/read").session(first)).andExpect(status().isOk());
        assertEquals(originalReadAt, jdbc.queryForObject("select read_at from notification where notification_id=?",
                LocalDateTime.class, itemId));
        mvc.perform(get("/api/notifications/inbox").session(first).param("status", "READ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/notifications/unread-count").session(first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        mvc.perform(patch("/api/notifications/read-all").session(first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/notifications/unread-count").session(second))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
        mvc.perform(get("/api/notifications/inbox").session(customerSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications/inbox")).andExpect(status().isUnauthorized());
        jdbc.update("update notification set target_route='https://example.invalid' where notification_id=?", itemId);
        mvc.perform(get("/api/notifications/inbox").session(first).param("type", "BOOKING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].targetRoute").doesNotExist());
        mvc.perform(post("/api/bookings/" + bookingId + "/cancel").session(customerSession))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notifications/inbox").session(second).param("type", "BOOKING_CANCELLED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].targetRoute").value("/bookings/" + bookingId));
    }

    @Test void fleetExpiryRemindersLinkResourcesAndDeduplicateEachThreshold() {
        Long adminId = jdbc.queryForObject(
                "insert into admin(admin_name) values('Expiry reviewer') returning admin_id", Long.class);
        LocalDate expiry = LocalDate.now(java.time.ZoneId.of("Asia/Kuala_Lumpur")).plusDays(7);
        jdbc.update("update driver set driver_license_expiry=? where driver_id=?", expiry.toString(), driver);
        jdbc.update("update vehicle set vehicle_roadtaxexpiry=? where vehicle_id=?", expiry, vehicle);

        expiryReminders.sendExpiryReminders();
        expiryReminders.sendExpiryReminders();

        assertEquals(List.of("/drivers/" + driver, "/vehicles/" + vehicle), jdbc.queryForList("""
                select target_route from notification
                where recipient_role='ADMIN' and recipient_id=?
                  and notification_type in ('DRIVER_LICENSE_EXPIRY','VEHICLE_ROAD_TAX_EXPIRY')
                order by target_route
                """, String.class, adminId));
    }

    @Test void calendarSourcesExposeScheduledPickupAndAssignedResources() throws Exception {
        long bookingId = (Long) create().get("bookingId");
        var storedFare = jdbc.queryForObject("select booking_tripfare from booking where booking_id=?",
                java.math.BigDecimal.class, bookingId);
        assignments.confirmAndAssign(new AssignmentRequest(bookingId, driver, vehicle, start.plusHours(3)),
                new java.math.BigDecimal("9999"));
        assertEquals(storedFare, jdbc.queryForObject("select booking_tripfare from booking where booking_id=?",
                java.math.BigDecimal.class, bookingId));
        var admin = new org.springframework.mock.web.MockHttpSession();
        var adminId = jdbc.queryForObject(
                "insert into admin(admin_name,admin_status) values('Calendar reviewer','ACTIVE') returning admin_id",
                Long.class);
        admin.setAttribute("adminId", adminId);

        mvc.perform(get("/api/bookings").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value(bookingId))
                .andExpect(jsonPath("$[0].bookingTripdate").value(start.toLocalDate().toString()))
                .andExpect(jsonPath("$[0].bookingPickuptime").value("10:00:00"));
        mvc.perform(get("/api/assignments").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value(bookingId))
                .andExpect(jsonPath("$[0].driverId").value(driver))
                .andExpect(jsonPath("$[0].vehicleId").value(vehicle))
                .andExpect(jsonPath("$[0].vehiclePlate").isNotEmpty());
        mvc.perform(get("/api/customers").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].custId").value(customer))
                .andExpect(jsonPath("$[0].custName").value("QA Customer"));
    }

    @Test void dashboardKeepsOperationalMetricsAfterEarningsRemoval() throws Exception {
        var admin = new org.springframework.mock.web.MockHttpSession();
        var adminId = jdbc.queryForObject(
                "insert into admin(admin_name,admin_status) values('Dashboard reviewer','ACTIVE') returning admin_id",
                Long.class);
        admin.setAttribute("adminId", adminId);
        mvc.perform(get("/api/dashboard/operations").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.today_trips").isNumber())
                .andExpect(jsonPath("$.verified_payments_month").isNumber())
                .andExpect(jsonPath("$.issued_invoice_balance").isNumber())
                .andExpect(jsonPath("$.commission_month").doesNotExist());
        mvc.perform(get("/api/admin/driver-earnings").param("month", "2035-01").session(admin))
                .andExpect(status().isNotFound());
    }

    @Test void expiryReleasesAvailabilityAndRequiresFreshAdminReservation() {
        long id = (Long) create().get("bookingId");
        jdbc.update("update booking_vehicle_hold set expires_at='2000-01-01' where booking_id=?", id);
        assertEquals("EXPIRED", assignments.preview(id, null, null).reservationStatus());
        assertTrue(trips.quote(request(3,1,1,0)).available());
        trips.notifyExpiredReservations();
        trips.notifyExpiredReservations();
        assertEquals(1, jdbc.queryForObject("select count(*) from notification where notification_type='BOOKING_RESERVATION_EXPIRED'", Integer.class));
        var choice = new AssignmentRequest(id, driver, vehicle, start.plusHours(3));
        assertEquals(409, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> assignments.confirmAndAssign(choice, null)).getStatusCode().value());
        assertEquals("PENDING", bookings.getCustomerBookings(customer).get(0).status());
        assertEquals("ACTIVE", assignments.changeSuggestedAssignment(choice).reservationStatus());
        assertEquals("CONFIRMED", assignments.confirmAndAssign(choice, null).bookingStatus());
        assertEquals(0, jdbc.queryForObject("select count(*) from booking_vehicle_hold", Integer.class));
    }

    @Test void categoryRecommendationUsesActualFleetCapacityAndSchedule() {
        addVehicle("MPV", 7, 9);
        assertEquals("Sedan", trips.quote(request(1,0,0,0)).recommendedVehicleType());
        assertEquals("Sedan", trips.quote(request(2,1,0,0)).recommendedVehicleType());
        assertEquals("MPV", trips.quote(request(2,0,0,2)).recommendedVehicleType());
        assertEquals("MPV", trips.quote(request(4,0,0,0)).recommendedVehicleType());
        assertEquals(0, jdbc.queryForObject("select count(*) from booking_vehicle_hold", Integer.class));
        long id = (Long) create().get("bookingId");
        assertEquals("MPV", trips.quote(request(3,0,0,0)).recommendedVehicleType());
        start = start.plusHours(2).plusMinutes(10);
        assertEquals("MPV", trips.quote(request(3,0,0,0)).recommendedVehicleType());
        start = start.plusMinutes(20);
        assertEquals("Sedan", trips.quote(request(3,0,0,0)).recommendedVehicleType());
        assertEquals("RESERVED", trips.resourceSchedule(driver, null, start.toLocalDate()).get(0).status());
        assertNull(bookings.getCustomerBookings(customer).get(0).assignment());
        assertTrue(assignments.getDriverAssignments(driver).isEmpty());
        trips.reject(id, "Customer requested a different schedule.");
        assertTrue(trips.resourceSchedule(driver, null, start.toLocalDate()).isEmpty());
    }

    @Test void inactiveDriverIsExcludedButLegacyOnTripFlagUsesTheSchedule() {
        jdbc.update("update driver set driver_status='INACTIVE' where driver_id=?", driver);
        assertEquals(CustomerTripService.AvailabilityStatus.DRIVER_UNAVAILABLE,
                trips.quote(request(1,0,0,0)).availabilityStatus());
        jdbc.update("update driver set driver_status='ACTIVE', driver_availability='On Trip' where driver_id=?", driver);
        assertTrue(trips.quote(request(1,0,0,0)).available());
    }

    @Test void overlappingSuggestedReplacementPreservesTheExistingHold() {
        long id = (Long) create().get("bookingId");
        long other = addVehicle("Sedan", 4, 10);
        long otherVehicle = jdbc.queryForObject("select vehicle_id from vehicle where driver_id=?", Long.class, other);
        create();
        var change = new AssignmentRequest(id, other, otherVehicle, start.plusHours(3));
        assertEquals(409, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> assignments.changeSuggestedAssignment(change)).getStatusCode().value());
        assertEquals(vehicle, jdbc.queryForObject("select vehicle_id from booking_vehicle_hold where booking_id=?", Long.class, id));
        assertEquals(driver, assignments.preview(id, null, null).preferred().driverId());
    }

    @Test void emergencyReassignmentKeepsFareAndRejectsInvalidReplacements() {
        start = LocalDateTime.now(java.time.ZoneId.of("Asia/Kuala_Lumpur")).plusMinutes(60).withNano(0);
        long id = (Long) create().get("bookingId");
        var end = start.plusHours(3);
        var previous = assignments.confirmAndAssign(new AssignmentRequest(id, driver, vehicle, end), null);
        long spare = addVehicle("Sedan", 2, 2);
        long spareVehicle = jdbc.queryForObject("select vehicle_id from vehicle where driver_id=?", Long.class, spare);
        var emergency = new AssignmentRequest(id, spare, spareVehicle, end, "Vehicle broke down", true);
        assertTrue(assignments.preview(id, null, previous.assignmentId()).alternatives().isEmpty());
        assertEquals(409, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> assignments.reassign(previous.assignmentId(), emergency)).getStatusCode().value());
        jdbc.update("update vehicle set vehicle_capacity=3 where vehicle_id=?", spareVehicle);
        assertEquals(409, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> assignments.reassign(previous.assignmentId(), emergency)).getStatusCode().value());
        jdbc.update("update vehicle set vehicle_luggagecapacity=5 where vehicle_id=?", spareVehicle);
        var ordinary = new AssignmentRequest(id, spare, spareVehicle, end, "Vehicle broke down");
        assertTrue(assignments.preview(id, null, previous.assignmentId()).emergencyRequired());
        assertEquals(409, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> assignments.reassign(previous.assignmentId(), ordinary)).getStatusCode().value());
        assertEquals(driver, assignments.getDriverAssignments(driver).get(0).driverId());
        var replacement = assignments.reassign(previous.assignmentId(), emergency);
        assertEquals(id, replacement.bookingId());
        assertEquals(previous.bookingFare(), replacement.bookingFare());
        assertEquals(previous.assignmentId(), replacement.previousAssignmentId());
        assertTrue(assignments.getDriverAssignments(driver).isEmpty());
        assertEquals(spare, assignments.getDriverAssignments(spare).get(0).driverId());
    }

    @Test void serviceFailureIsHttp503RatherThanScheduleUnavailability() throws Exception {
        when(routing.route(any())).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Routing unavailable"));
        var session=new org.springframework.mock.web.MockHttpSession();session.setAttribute("mobileRole","CUSTOMER");session.setAttribute("mobileUserId",customer);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bookings/availability").session(session)
            .contentType("application/json").content(json.writeValueAsString(request(3,1,1,0))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.availabilityStatus").value("SERVICE_ERROR"));
    }
    @Test void existingDriverTripBlocksOtherwiseFreeCurrentVehicle() {
        long otherDriver=addVehicle("Van",8,10);
        long historicalVehicle=jdbc.queryForObject("select vehicle_id from vehicle where driver_id=?",Long.class,otherDriver);
        long existing=((Number)create().get("bookingId")).longValue();
        // Historical trip vehicle need not be the driver's current registered vehicle.
        jdbc.update("update booking set booking_status='CONFIRMED' where booking_id=?",existing);
        jdbc.update("insert into driver_assignment(booking_id,driver_id,vehicle_id,assignment_date,assignment_status,scheduled_end_at) values(?,?,?,localtimestamp,'ASSIGNED',?)",existing,driver,historicalVehicle,start.plusHours(3));
        assertFalse(trips.hasConflict(vehicle,null,start,start.plusHours(3),null));
        assertTrue(trips.hasConflict(null,driver,start,start.plusHours(3),null));
        assertFalse(trips.quote(request(3,1,1,0)).available());
    }
    @Test void canonicalApiCreatesOwnedPendingBookingAndMineReturnsSavedSummary() throws Exception {
        var session=new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("mobileRole","CUSTOMER");session.setAttribute("mobileUserId",customer);
        var body=json.valueToTree(approved(request(3,1,1,0)));
        ((com.fasterxml.jackson.databind.node.ObjectNode)body).put("custId",999999);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bookings/customer")
            .session(session).header("Idempotency-Key","http-creation").contentType("application/json").content(body.toString()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.bookingStatus").value("PENDING"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.summary.smallCount").value(1));
        assertEquals(customer,jdbc.queryForObject("select cust_id from booking",Long.class));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/bookings/mine").session(session))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].tripFare").value(116))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].luggageUnits").value(3));
    }
    @Test void optionalFlightSchedulePersistsWithoutChangingPickup() throws Exception {
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("mobileRole", "CUSTOMER");
        session.setAttribute("mobileUserId", customer);
        var base = request(2, 0, 0, 0);
        var trip = new TripQuoteRequest(
                base.pickupAddress(), base.destinationAddress(), base.pickupLatitude(),
                base.pickupLongitude(), base.destinationLatitude(), base.destinationLongitude(),
                base.scheduledAt(), base.passengerCount(), 0, 0, 0, "NORMAL_TRANSFER",
                "  MH123  ", null, null, null, base.scheduledAt().toLocalDate().minusDays(1),
                LocalTime.of(8, 17));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/bookings/customer/complete").session(session)
                .header("Idempotency-Key", "flight-schedule").contentType("application/json")
                .content(json.writeValueAsString(approved(trip))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.summary.flightNo").value("MH123"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.summary.flightDate")
                    .value(trip.flightDate().toString()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.summary.flightTime").exists());

        assertEquals(trip.scheduledAt().toLocalTime(),
                jdbc.queryForObject("select booking_pickuptime from booking", LocalTime.class));
        assertEquals(trip.flightDate(),
                jdbc.queryForObject("select booking_flight_date from booking", LocalDate.class));
        assertEquals(trip.flightTime(),
                jdbc.queryForObject("select booking_flight_time from booking", LocalTime.class));
    }
    @Test void estimateRequiresSessionAndValidCounts() throws Exception {
        String body=json.writeValueAsString(request(3,1,1,0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bookings/estimate").contentType("application/json").content(body))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        var session=new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("mobileRole","CUSTOMER");session.setAttribute("mobileUserId",customer);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bookings/estimate").session(session)
            .contentType("application/json").content(json.writeValueAsString(request(0,-1,0,0))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
}
