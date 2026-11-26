package com.diladrive.service;

import com.diladrive.entity.Booking;
import com.diladrive.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "RUN_TRIP_FINANCE_IT", matches = "true")
class TripFinancePostgresIT {
    @Test
    void preservesBookingChargesAndIssuedInvoiceAmounts() throws Exception {
        var properties = new Properties();
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(input);
        }
        var environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new PropertiesPropertySource("application", properties));
        String url = environment.getRequiredProperty("spring.datasource.url");
        String username = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getRequiredProperty("spring.datasource.password");
        String schema = "diladrive_finance_it_" + System.nanoTime();
        var server = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        server.execute("create schema " + schema);
        try {
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, username, password));
            jdbc.execute(Files.readString(Path.of("../../database/schema/BASELINE_CURRENT.sql")));
            jdbc.update("insert into admin(admin_id,admin_name) values(1,'Test admin')");
            jdbc.update("insert into driver(driver_id,driver_name) values(1,'Test driver')");
            jdbc.update("insert into vehicle(vehicle_id,driver_id) values(1,1)");
            jdbc.update("insert into booking(booking_id,booking_tripfare,booking_status) values(1,160,'COMPLETED')");
            jdbc.update("""
                    insert into driver_assignment(assignment_id,booking_id,driver_id,vehicle_id,assignment_status)
                    values(1,1,1,1,'COMPLETED')
                    """);
            jdbc.execute(Files.readString(Path.of("../../database/schema/026_trip_charges_and_commission.sql")));
            assertEquals(new BigDecimal("160.00"), jdbc.queryForObject(
                    "select booking_tripfare from booking where booking_id=1", BigDecimal.class));

            var bookings = mock(BookingRepository.class);
            var booking = new Booking();
            booking.setBookingId(1L);
            booking.setBookingTripfare(new BigDecimal("160.00"));
            booking.setBookingStatus("COMPLETED");
            when(bookings.findById(1L)).thenReturn(Optional.of(booking));
            when(bookings.findByIdForUpdate(1L)).thenReturn(Optional.of(booking));
            var finance = new TripFinanceService(jdbc, bookings);
            var extras = new TripFinanceService.Charges(
                    new BigDecimal("1"), new BigDecimal("12"), new BigDecimal("5"), "Actual costs");
            finance.saveCharges(1, extras, 1);
            finance.saveCharges(1, extras, 1);
            assertEquals(1, jdbc.queryForObject("select count(*) from trip_charge_revision", Integer.class));
            assertEquals(new BigDecimal("237.00"), finance.finalFare(booking));

            jdbc.update("insert into customer(cust_id,cust_name) values(1,'Test customer')");
            jdbc.update("""
                    insert into documents(document_number,document_type,booking_id,cust_id,cust_name,amount,status)
                    values('TEST-INV','INVOICE',1,1,'Test customer',237,'ISSUED')
                    """);
            finance.saveCharges(1, new TripFinanceService.Charges(new BigDecimal("1"),
                    new BigDecimal("15"), new BigDecimal("5"), "Corrected toll"), 1);
            assertEquals(new BigDecimal("240.00"), finance.finalFare(booking));
            assertEquals(new BigDecimal("237.00"), finance.payableFare(booking));
            assertEquals(new BigDecimal("237.00"), jdbc.queryForObject("select amount from documents", BigDecimal.class));
            assertEquals(new BigDecimal("3.00"), jdbc.queryForObject(
                    "select invoice_adjustment from trip_charge_revision order by id desc limit 1", BigDecimal.class));
            assertEquals(Map.of(), finance.customerTotals(1));
        } finally {
            server.execute("drop schema " + schema + " cascade");
        }
    }
}
