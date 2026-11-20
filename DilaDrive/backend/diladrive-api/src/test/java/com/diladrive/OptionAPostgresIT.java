package com.diladrive;

import com.diladrive.dto.*;
import com.diladrive.entity.*;
import com.diladrive.repository.*;
import com.diladrive.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs only against an explicitly provisioned disposable schema; test data never uses the application schema. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="OPTION_A_TEST_SCHEMA", matches="diladrive_option_a_check_[0-9]+")
class OptionAPostgresIT {
    @Autowired DriverService drivers;
    @Autowired VehicleService vehicles;
    @Autowired DriverRepository driverRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired BookingRepository bookings;
    @Autowired AssignmentServiceImpl assignments;
    @Autowired MobileAuthService auth;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach void requireIsolatedSchema() {
        assertEquals(System.getenv("OPTION_A_TEST_SCHEMA"), jdbc.queryForObject("select current_schema()", String.class));
    }

    private Driver createDriver() {
        return drivers.createDriver(request("QA" + UUID.randomUUID().toString().substring(0,8)));
    }
    private DriverCreateRequest request(String plate) {
        return new DriverCreateRequest("QA Driver", UUID.randomUUID()+"@example.test", "password123",
                "0123456789", "IC", "LICENSE", "2029-01-01", "Company Driver", "PENDING", "AVAILABLE",
                new VehicleCreateRequest("QA Model", plate, "MPV", 7, LocalDate.of(2029,1,1), LocalDate.of(2029,1,1), "AVAILABLE", 5));
    }
    private MockHttpSession adminSession() {
        MockHttpSession session = new MockHttpSession(); session.setAttribute("adminId", 1L); return session;
    }

    @Test void createsActiveHashedPairAndLoadsAndEditsSameVehicleViaHttp() throws Exception {
        Driver driver = createDriver();
        entityManager.flush();
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        Vehicle vehicle = vehicles.getAssignedVehicle(driver.getDriverId());
        assertNotNull(vehicle);
        assertEquals("ACTIVE", driver.getDriverStatus());
        assertNotEquals("password123", driver.getDriverPassword());
        mvc.perform(get("/api/vehicles/"+vehicle.getVehicleId()).session(adminSession()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.driverId").value(driver.getDriverId()));
        String body = "{\"vehicleModel\":\"Updated Model\",\"vehiclePlateno\":\""+vehicle.getVehiclePlateno()+
                "\",\"vehicleType\":\"MPV\",\"vehicleCapacity\":8,\"vehicleLuggageCapacity\":6,\"vehicleStatus\":\"INACTIVE\"}";
        mvc.perform(put("/api/vehicles/"+vehicle.getVehicleId()).session(adminSession()).contentType("application/json").content(body))
                .andExpect(status().isOk());
        entityManager.flush(); entityManager.clear();
        Vehicle reloaded = vehicles.getVehicle(vehicle.getVehicleId());
        assertEquals("Updated Model", reloaded.getVehicleModel());
        assertEquals("INACTIVE", reloaded.getVehicleStatus());
        assertEquals(driver.getDriverId(), reloaded.getDriverId());
        assertEquals(1, vehicleRepository.findByDriverId(driver.getDriverId()).size());
        mvc.perform(get("/api/vehicles/"+vehicle.getVehicleId()).session(adminSession()))
                .andExpect(jsonPath("$.vehicleModel").value("Updated Model"));
    }

    @Test void activeLoginAndDisabledLoginKeepVehicleIndependent() {
        Driver driver = createDriver();
        MobileLoginRequest login = new MobileLoginRequest();
        login.setEmail(driver.getDriverEmail()); login.setPassword("password123"); login.setRole("DRIVER");
        assertTrue(auth.login(login).isPresent());
        driver.setDriverStatus("DISABLED"); driverRepository.saveAndFlush(driver);
        assertTrue(auth.login(login).isEmpty());
        driver.setDriverStatus("ACTIVE"); driverRepository.saveAndFlush(driver);
        assertTrue(auth.login(login).isPresent());
    }

    @Test void legacyRegistrationIsExplicitAndNeverCreatesSecondVehicle() throws Exception {
        Long id = jdbc.queryForObject("select driver_id from driver where driver_email='option-a-legacy@example.test'", Long.class);
        assertNull(drivers.getDriver(id).vehicle());
        mvc.perform(get("/api/drivers/"+id).session(adminSession())).andExpect(status().isOk());
        VehicleCreateRequest registration = new VehicleCreateRequest("Legacy Model", "LEGACYQA", "MPV", 7, null, null, "AVAILABLE", 3);
        Vehicle registered = drivers.registerMissingVehicle(id, registration);
        entityManager.flush(); jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        assertEquals(id, registered.getDriverId());
        var error = assertThrows(ResponseStatusException.class, () -> drivers.registerMissingVehicle(id, registration));
        assertEquals(409, error.getStatusCode().value());
        assertEquals(1, vehicleRepository.findByDriverId(id).size());
    }

    @Test void linkedDriverEditUpdatesExistingVehicleAndPreservesPair() {
        Driver driver = createDriver(); Vehicle original = vehicles.getAssignedVehicle(driver.getDriverId());
        var update = new DriverUpdateRequest();
        update.setDriverName("Updated Driver"); update.setDriverEmail(driver.getDriverEmail()); update.setDriverPhoneno("0123456789");
        update.setDriverStatus("ACTIVE"); update.setDriverAvailability("BUSY");
        update.setVehicle(new VehicleCreateRequest("Edited Model", original.getVehiclePlateno(), "MPV", 8, null, null, "MAINTENANCE", 4));
        var result = drivers.updateDriverDetails(driver.getDriverId(), update);
        entityManager.flush(); entityManager.clear();
        assertEquals(original.getVehicleId(), result.vehicle().getVehicleId());
        assertEquals("Edited Model", vehicles.getVehicle(original.getVehicleId()).getVehicleModel());
        assertEquals("BUSY", driverRepository.findById(driver.getDriverId()).orElseThrow().getDriverAvailability());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void duplicatePlateRollsBackTheDriverAndVehicleTogether() {
        Driver first = createDriver(); String plate = vehicles.getAssignedVehicle(first.getDriverId()).getVehiclePlateno();
        long before = driverRepository.count();
        assertThrows(ResponseStatusException.class, () -> drivers.createDriver(request(plate)));
        assertEquals(before, driverRepository.count());
        assertEquals(1, vehicleRepository.findByDriverId(first.getDriverId()).size());
    }

    @Test void schedulesNonOverlappingBookingsAndRejectsOverlapAndUnsafeVehicleEdits() {
        Driver driver = createDriver(); Vehicle vehicle = vehicles.getAssignedVehicle(driver.getDriverId());
        Booking first = bookingAt(9,0), second = bookingAt(10,0), overlapping = bookingAt(9,30);
        var one = assignments.createAssignment(new AssignmentRequest(first.getBookingId(), driver.getDriverId(), null,
                first.getBookingTripdate().atTime(10,0)));
        var two = assignments.createAssignment(new AssignmentRequest(second.getBookingId(), driver.getDriverId(), null,
                second.getBookingTripdate().atTime(11,0)));
        assertEquals(vehicle.getVehicleId(), one.vehicleId());
        assertEquals(vehicle.getVehicleId(), two.vehicleId());
        assertEquals(driver.getDriverId(), first.getDriverId());
        var error = assertThrows(ResponseStatusException.class, () -> assignments.createAssignment(new AssignmentRequest(
                overlapping.getBookingId(), driver.getDriverId(), null, overlapping.getBookingTripdate().atTime(10,30))));
        assertEquals(409, error.getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> vehicles.updateVehicle(vehicle.getVehicleId(), vehicle));
    }

    @Test void legacyCompletionThroughDriverUpdateIsAtomicAndExplicit() {
        Long id = jdbc.queryForObject("select driver_id from driver where driver_email='option-a-legacy@example.test'", Long.class);
        var update = new DriverUpdateRequest(); update.setDriverName("Completed legacy profile");
        update.setDriverEmail("option-a-legacy@example.test"); update.setDriverPhoneno("0123456789");
        update.setDriverStatus("ACTIVE"); update.setDriverAvailability("AVAILABLE"); update.setRegisterMissingVehicle(true);
        update.setVehicle(new VehicleCreateRequest("Legacy Model", "LEGACYPUTQA", "MPV", 7, null, null, "AVAILABLE", 3));
        var result = drivers.updateDriverDetails(id, update);
        entityManager.flush(); jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE"); entityManager.clear();
        assertEquals("Completed legacy profile", driverRepository.findById(id).orElseThrow().getDriverName());
        assertEquals(id, result.vehicle().getDriverId());
        assertEquals(1, vehicleRepository.findByDriverId(id).size());
    }

    @Test void databasePreventsSecondCurrentVehicleAndUnpairedNewDriver() {
        Driver driver = createDriver();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into vehicle(vehicle_model,vehicle_plateno,vehicle_type,vehicle_capacity,driver_id) values ('QA','SECONDQA','MPV',7,?)",
                driver.getDriverId()));
    }

    @Test void databaseDeferredCheckRejectsCreatingDriverWithoutVehicle() {
        jdbc.update("insert into driver(driver_name,driver_email,driver_password) values ('Unpaired QA','unpaired-qa@example.test','hashed-test')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE"));
    }

    @Test void reassignsDriverAndTheirLinkedVehicleTogetherWithoutDeletingHistory() {
        Driver firstDriver = createDriver(), secondDriver = createDriver();
        Booking booking = bookingAt(9,0);
        var original = assignments.createAssignment(new AssignmentRequest(booking.getBookingId(), firstDriver.getDriverId(), null,
                booking.getBookingTripdate().atTime(10,0)));
        var changed = assignments.reassign(original.assignmentId(), new AssignmentRequest(null, secondDriver.getDriverId(), null));
        entityManager.flush(); entityManager.clear();
        assertEquals(original.assignmentId(), changed.assignmentId());
        assertEquals(secondDriver.getDriverId(), changed.driverId());
        assertEquals(vehicles.getAssignedVehicle(secondDriver.getDriverId()).getVehicleId(), changed.vehicleId());
        Booking reloaded = bookings.findById(booking.getBookingId()).orElseThrow();
        assertEquals(secondDriver.getDriverId(), reloaded.getDriverId());
        assertEquals(changed.vehicleId(), reloaded.getVehicleId());
        Booking reuse = bookingAt(9,0);
        assertNotNull(assignments.createAssignment(new AssignmentRequest(reuse.getBookingId(), firstDriver.getDriverId(), null,
                reuse.getBookingTripdate().atTime(10,0))));
    }

    private Booking bookingAt(int hour, int minute) {
        Long customer = jdbc.queryForObject("insert into customer(cust_name,cust_email,cust_status) values ('QA Customer',?, 'ACTIVE') returning cust_id",
                Long.class, UUID.randomUUID()+"@example.test");
        Booking booking = new Booking(); booking.setCustId(customer); booking.setBookingType("MPV");
        booking.setBookingStatus("CONFIRMED"); booking.setBookingTripdate(LocalDate.of(2028,1,1));
        booking.setBookingPickuptime(LocalTime.of(hour,minute)); booking.setBookingPassengercount(2); booking.setBookingLuggagecount(2);
        booking.setBookingPickupaddress("QA pickup"); booking.setBookingDestinationaddress("QA destination");
        return bookings.saveAndFlush(booking);
    }
}
