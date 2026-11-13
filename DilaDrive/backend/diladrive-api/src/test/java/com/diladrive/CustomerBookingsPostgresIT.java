package com.diladrive;

import com.diladrive.entity.Booking;
import com.diladrive.repository.BookingRepository;
import com.diladrive.repository.CustomerRepository;
import com.diladrive.repository.NotificationRepository;
import com.diladrive.repository.AssignmentRepository;
import com.diladrive.repository.DriverRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Read-only check for a locally configured PostgreSQL database.
 * Run explicitly with: mvn -Dtest=CustomerBookingsPostgresIT test
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional(readOnly = true)
class CustomerBookingsPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void customerSessionReturnsOnlyPersistedBookingsInApiOrder() throws Exception {
        var customers = customerRepository.findAll();
        assumeFalse(customers.isEmpty(), "Requires at least one existing customer row");
        Long customerId = customers.get(0).getCustId();

        List<Long> expectedIds = bookingRepository.findAll().stream()
                .filter(booking -> customerId.equals(booking.getCustId()))
                .sorted(Comparator
                        .comparing(Booking::getBookingCreatedat,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Booking::getBookingId, Comparator.reverseOrder()))
                .map(Booking::getBookingId)
                .toList();

        MockHttpSession session = customerSession(customerId);
        String response = mockMvc.perform(get("/api/bookings/mine").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode rows = objectMapper.readTree(response);
        List<Long> actualIds = new java.util.ArrayList<>();
        rows.forEach(row -> actualIds.add(row.get("bookingId").asLong()));
        assertEquals(expectedIds, actualIds);

        mockMvc.perform(get("/api/bookings/mine")
                        .session(customerSession(customerId))
                        .sessionAttr("mobileRole", "DRIVER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/bookings/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerSessionReturnsItsPersistedProfile() throws Exception {
        var customers = customerRepository.findAll();
        assumeFalse(customers.isEmpty(), "Requires at least one existing customer row");
        var customer = customers.get(0);

        String response = mockMvc.perform(get("/api/customers/me")
                        .session(customerSession(customer.getCustId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode profile = objectMapper.readTree(response);
        assertEquals(customer.getCustId(), profile.get("id").asLong());
        assertEquals(customer.getCustName(), profile.get("name").asText());
        assertEquals(customer.getCustEmail(), profile.get("email").asText());

        mockMvc.perform(get("/api/customers/me")
                        .session(customerSession(customer.getCustId()))
                        .sessionAttr("mobileRole", "DRIVER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customers/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerSessionReturnsOnlyItsPersistedNotifications() throws Exception {
        var customers = customerRepository.findAll();
        assumeFalse(customers.isEmpty(), "Requires at least one existing customer row");
        Long customerId = customers.get(0).getCustId();
        List<Long> expectedIds = notificationRepository
                .findByRecipientRoleIgnoreCaseAndRecipientIdOrderBySentAtDesc(
                        "CUSTOMER", customerId)
                .stream()
                .map(notification -> notification.getNotificationId())
                .toList();

        String response = mockMvc.perform(get("/api/notifications/customer/mine")
                        .session(customerSession(customerId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode rows = objectMapper.readTree(response);
        List<Long> actualIds = new java.util.ArrayList<>();
        rows.forEach(row -> {
            assertEquals("CUSTOMER", row.get("recipientRole").asText());
            assertEquals(customerId.longValue(), row.get("recipientId").asLong());
            actualIds.add(row.get("notificationId").asLong());
        });
        assertEquals(expectedIds, actualIds);

        mockMvc.perform(get("/api/notifications/customer/mine")
                        .session(customerSession(customerId))
                        .sessionAttr("mobileRole", "DRIVER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/notifications/customer/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void driverSessionReturnsItsPersistedProfile() throws Exception {
        var drivers = driverRepository.findAll();
        assumeFalse(drivers.isEmpty(), "Requires at least one existing driver row");
        var driver = drivers.get(0);

        String response = mockMvc.perform(get("/api/drivers/me")
                        .session(driverSession(driver.getDriverId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode profile = objectMapper.readTree(response);
        assertEquals(driver.getDriverId(), profile.get("id").asLong());
        assertEquals(driver.getDriverName(), profile.get("name").asText());
        assertEquals(driver.getDriverEmail(), profile.get("email").asText());

        mockMvc.perform(get("/api/drivers/me")
                        .session(driverSession(driver.getDriverId()))
                        .sessionAttr("mobileRole", "CUSTOMER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/drivers/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void driverSessionReturnsOnlyItsPersistedAssignments() throws Exception {
        var drivers = driverRepository.findAll();
        assumeFalse(drivers.isEmpty(), "Requires at least one existing driver row");
        Long driverId = drivers.get(0).getDriverId();
        List<Long> expectedIds = assignmentRepository
                .findAssignmentSummariesByDriverId(driverId)
                .stream()
                .map(summary -> summary.getAssignmentId())
                .toList();

        String response = mockMvc.perform(get("/api/assignments/mine")
                        .session(driverSession(driverId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode rows = objectMapper.readTree(response);
        List<Long> actualIds = new java.util.ArrayList<>();
        rows.forEach(row -> {
            assertEquals(driverId.longValue(), row.get("driverId").asLong());
            actualIds.add(row.get("assignmentId").asLong());
        });
        assertEquals(expectedIds, actualIds);

        mockMvc.perform(get("/api/assignments/mine")
                        .session(driverSession(driverId))
                        .sessionAttr("mobileRole", "CUSTOMER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/assignments/mine"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpSession customerSession(Long customerId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("mobileRole", "CUSTOMER");
        session.setAttribute("mobileUserId", customerId);
        return session;
    }

    private MockHttpSession driverSession(Long driverId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("mobileRole", "DRIVER");
        session.setAttribute("mobileUserId", driverId);
        return session;
    }
}
