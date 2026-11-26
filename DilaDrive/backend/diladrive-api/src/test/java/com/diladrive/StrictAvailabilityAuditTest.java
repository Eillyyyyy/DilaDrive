package com.diladrive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Opt-in read-only audit: actual fleet/rates and real road router, no fixtures or writes. */
@EnabledIfEnvironmentVariable(named="RUN_STRICT_AVAILABILITY_AUDIT",matches="true")
@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate","spring.datasource.hikari.read-only=true",
    "spring.datasource.hikari.data-source-properties.options=-c default_transaction_read_only=on",
    "booking.routing-url=https://router.project-osrm.org","spring.jpa.show-sql=false",
    "booking.pickup-check-enabled=false","booking.reservation-sweep-ms=3600000",
    "logging.level.com.diladrive.service.CustomerTripService=DEBUG"})
@AutoConfigureMockMvc
class StrictAvailabilityAuditTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Test void traceActualFleetRequestWithoutChangingCompanyData() throws Exception {
        assertEquals("on",jdbc.queryForObject("show default_transaction_read_only",String.class));
        assertEquals("on",jdbc.queryForObject("show transaction_read_only",String.class));
        // Known public landmarks also used by PricingPostgresIT.
        var request=json.createObjectNode()
                .put("pickupAddress", "UiTM Kampus Jasin").put("pickupLatitude", 2.2254670).put("pickupLongitude", 102.4556074)
                .put("destinationAddress", "KLIA Terminal 1").put("destinationLatitude", 2.7527291).put("destinationLongitude", 101.7055234)
                .put("tripType", "NORMAL_TRANSFER");
        request.put("scheduledAt",LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")).plusDays(1).atTime(10,0).toString());
        request.put("passengerCount",4);request.put("smallCount",1);request.put("mediumCount",1);request.put("largeCount",0);
        Long customer=jdbc.queryForObject("select min(cust_id) from customer where upper(cust_status)='ACTIVE'",Long.class);
        assertNotNull(customer,"Audit requires an existing approved customer; do not create fake company data.");
        var session=new MockHttpSession();session.setAttribute("mobileRole","CUSTOMER");session.setAttribute("mobileUserId",customer);
        var response=mvc.perform(post("/api/bookings/availability").session(session).contentType("application/json")
                .content(json.writeValueAsString(request))).andReturn().getResponse();
        assertTrue(response.getStatus()==200 || response.getStatus()==503,response.getContentAsString());
        var body=json.readTree(response.getContentAsString());
        assertNotNull(body.get("availabilityStatus"));
        if(response.getStatus()==503) assertEquals("SERVICE_ERROR",body.path("availabilityStatus").asText());
        Files.writeString(Path.of("target/strict-availability-actual-request.json"),json.writeValueAsString(request));
        Files.writeString(Path.of("target/strict-availability-actual-response.json"),response.getContentAsString());
        System.out.printf("ACTUAL FLEET AUDIT: HTTP %d, %s, category=%s%n", response.getStatus(),
                body.path("availabilityStatus").asText(), body.path("recommendedVehicleType").asText());
        org.junit.jupiter.api.Assumptions.assumeTrue(response.getStatus() == 200, "The external road router is unavailable.");
        request.put("largeCount",100);
        var capacity=mvc.perform(post("/api/bookings/availability").session(session).contentType("application/json")
                .content(json.writeValueAsString(request))).andReturn().getResponse();
        assertEquals(200,capacity.getStatus());
        assertEquals("LUGGAGE_CAPACITY_UNAVAILABLE",json.readTree(capacity.getContentAsString()).path("availabilityStatus").asText());
    }
}
