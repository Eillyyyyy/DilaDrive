package com.diladrive;

import com.diladrive.dto.*;
import com.diladrive.entity.*;
import com.diladrive.repository.*;
import com.diladrive.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Committed fixtures are confined to an explicitly created disposable schema. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="BOOKING_ASSIGNMENT_TEST_SCHEMA", matches="diladrive_booking_check_[0-9]+")
class BookingConfirmationPostgresIT {
    @Autowired DriverService drivers;
    @Autowired VehicleService vehicles;
    @Autowired BookingRepository bookings;
    @Autowired AssignmentRepository repository;
    @Autowired AssignmentServiceImpl service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PasswordService passwords;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @org.springframework.boot.test.web.server.LocalServerPort int port;

    @BeforeEach void isolated() {
        assertEquals(System.getenv("BOOKING_ASSIGNMENT_TEST_SCHEMA"), jdbc.queryForObject("select current_schema()", String.class));
    }
    Driver driver() {
        String key=UUID.randomUUID().toString();
        return drivers.createDriver(new DriverCreateRequest("QA Driver",key+"@example.test","password123",
            "0123456789","IC","LICENSE","2035-01-01","Company Driver","ACTIVE","AVAILABLE",
            new VehicleCreateRequest("QA Model",key.substring(0, 8),"MPV",7,LocalDate.of(2035,1,1),LocalDate.of(2035,1,1),"AVAILABLE",5)));
    }
    Booking booking(int hour, int minute) {
        Long customer=jdbc.queryForObject("insert into customer(cust_name,cust_email,cust_status) values ('QA Customer',?,'ACTIVE') returning cust_id",Long.class,UUID.randomUUID()+"@example.test");
        Booking b=new Booking(); b.setCustId(customer); b.setBookingStatus("PENDING"); b.setBookingType("MPV");
        b.setBookingTripdate(LocalDate.of(2028,1,1)); b.setBookingPickuptime(LocalTime.of(hour,minute));
        b.setBookingPassengercount(2); b.setBookingLuggagecount(2);
        b.setBookingPickupaddress("QA Pickup"); b.setBookingDestinationaddress("QA Destination");
        return bookings.saveAndFlush(b);
    }
    AssignmentRequest request(Booking b,Driver d,int hour,int minute) {
        return new AssignmentRequest(b.getBookingId(),d.getDriverId(),null,b.getBookingTripdate().atTime(hour,minute));
    }
    MockHttpSession admin() { var s=new MockHttpSession();s.setAttribute("adminId",1L);return s; }
    MockHttpSession mobile(Driver d) { var s=new MockHttpSession();s.setAttribute("mobileRole","DRIVER");s.setAttribute("mobileUserId",d.getDriverId());return s; }
    long count(Booking b) { return jdbc.queryForObject("select count(*) from driver_assignment where booking_id=?",Long.class,b.getBookingId()); }

    @Test void pendingConfirmPersistsAndAllApisShowSameAssignmentAndDriverOwnership() throws Exception {
        Driver d=driver(),other=driver(); Booking b=booking(9,0);
        mvc.perform(post("/api/bookings/"+b.getBookingId()+"/confirm-and-assign").session(admin()).contentType("application/json")
          .content("{\"driverId\":"+d.getDriverId()+",\"scheduledEndAt\":\"2028-01-01T10:00:00\",\"tripFare\":145}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.assignmentStatus").value("ASSIGNED"))
          .andExpect(jsonPath("$.driverId").value(d.getDriverId()));
        Booking saved=bookings.findById(b.getBookingId()).orElseThrow();
        assertEquals("CONFIRMED",saved.getBookingStatus()); assertEquals(d.getDriverId(),saved.getDriverId());
        assertEquals(vehicles.getAssignedVehicle(d.getDriverId()).getVehicleId(),saved.getVehicleId()); assertEquals(1,count(b));
        var actual=service.getAllAssignments().stream().filter(a->a.bookingId().equals(b.getBookingId())).findFirst().orElseThrow();
        mvc.perform(get("/api/assignments/"+actual.assignmentId()).session(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.vehicleId").value(saved.getVehicleId()));
        mvc.perform(get("/api/assignments/mine").session(mobile(d))).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.assignmentId == "+actual.assignmentId()+")]").isNotEmpty());
        mvc.perform(get("/api/assignments/mine").session(mobile(other))).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.assignmentId == "+actual.assignmentId()+")]").isEmpty());
        mvc.perform(patch("/api/assignments/"+actual.assignmentId()+"/status").session(mobile(other)).contentType("application/json").content("{\"status\":\"OTW\"}"))
          .andExpect(status().isNotFound());
    }
    @Test void conflictRollsBackFareConfirmationPointersAndNotifications() {
        Driver d=driver(); Booking first=booking(9,0), clash=booking(9,30);
        service.confirmAndAssign(request(first,d,10,0),new BigDecimal("100"));
        long notifications=jdbc.queryForObject("select count(*) from notification",Long.class);
        assertThrows(ResponseStatusException.class,()->service.confirmAndAssign(request(clash,d,10,30),new BigDecimal("200")));
        Booking saved=bookings.findById(clash.getBookingId()).orElseThrow();
        assertEquals("PENDING",saved.getBookingStatus()); assertNull(saved.getDriverId()); assertNull(saved.getBookingTripfare());
        assertEquals(0,count(clash));assertEquals(notifications,jdbc.queryForObject("select count(*) from notification",Long.class));
    }
    @Test void retryAndConcurrentRequestsProduceExactlyOneAssignment() throws Exception {
        Driver d=driver();Booking b=booking(9,0);var request=request(b,d,10,0);
        var gate=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        Callable<Boolean> action=()->{gate.await();try{service.confirmAndAssign(request,new BigDecimal("100"));return true;}catch(ResponseStatusException e){assertEquals(409,e.getStatusCode().value());return false;}};
        try {
            var a=executor.submit(action);var c=executor.submit(action);gate.countDown();
            assertNotEquals(a.get(30,TimeUnit.SECONDS),c.get(30,TimeUnit.SECONDS));
        } finally {executor.shutdownNow();}
        assertEquals(1,count(b));
        assertThrows(ResponseStatusException.class,()->service.confirmAndAssign(request,new BigDecimal("100")));
        assertEquals(1,count(b));
    }
    @Test void legacyConfirmedRepairIsExplicitAndPendingPointersDoNotAutoConfirm() {
        Driver d=driver();Booking b=booking(9,0);b.setBookingStatus("CONFIRMED");b.setDriverId(d.getDriverId());b.setVehicleId(vehicles.getAssignedVehicle(d.getDriverId()).getVehicleId());bookings.saveAndFlush(b);
        assertEquals(0,count(b));assertEquals("CONFIRMED",bookings.findById(b.getBookingId()).orElseThrow().getBookingStatus());
        service.confirmAndAssign(request(b,d,10,0),new BigDecimal("100"));assertEquals(1,count(b));
        Booking pending=booking(11,0);pending.setDriverId(d.getDriverId());bookings.saveAndFlush(pending);
        assertEquals("PENDING",bookings.findById(pending.getBookingId()).orElseThrow().getBookingStatus());assertEquals(0,count(pending));
    }
    @Test void reassignUpdatesSameRecordAndTerminalAssignmentsAreProtected() {
        Driver d=driver(),replacement=driver();Booking b=booking(9,0);
        var initial=service.confirmAndAssign(request(b,d,10,0),new BigDecimal("100"));
        var changed=service.reassign(initial.assignmentId(),request(b,replacement,10,0));
        assertEquals(initial.assignmentId(),changed.assignmentId());assertEquals(1,count(b));
        assertEquals(replacement.getDriverId(),bookings.findById(b.getBookingId()).orElseThrow().getDriverId());
        service.completeAssignment(changed.assignmentId());
        assertThrows(ResponseStatusException.class,()->service.reassign(changed.assignmentId(),request(b,d,10,0)));
        Booking next=booking(11,0);var cancelled=service.confirmAndAssign(request(next,d,12,0),new BigDecimal("100"));
        service.updateStatus(cancelled.assignmentId(),"CANCELLED");
        assertThrows(ResponseStatusException.class,()->service.reassign(cancelled.assignmentId(),request(next,replacement,12,0)));
    }
    @Test void adjacentPeriodsAreAllowedButUnknownLegacyEndIsConservative() {
        Driver d=driver();Booking b=booking(9,0),next=booking(10,0);
        var first=service.confirmAndAssign(request(b,d,10,0),new BigDecimal("100"));
        assertNotNull(service.confirmAndAssign(request(next,d,11,0),new BigDecimal("100")));
        jdbc.update("update driver_assignment set scheduled_end_at=null where assignment_id=?",first.assignmentId());
        Booking later=booking(12,0);
        assertThrows(ResponseStatusException.class,()->service.confirmAndAssign(request(later,d,13,0),new BigDecimal("100")));
        assertEquals("PENDING",bookings.findById(later.getBookingId()).orElseThrow().getBookingStatus());
    }
    @Test void invalidPairAccountCapacityAndMissingEndAreRejectedWithoutSaving() {
        Driver d=driver(),other=driver();Booking b=booking(9,0);
        Long wrong=vehicles.getAssignedVehicle(other.getDriverId()).getVehicleId();
        assertThrows(ResponseStatusException.class,()->service.confirmAndAssign(new AssignmentRequest(b.getBookingId(),d.getDriverId(),wrong,b.getBookingTripdate().atTime(10,0)),BigDecimal.TEN));
        assertThrows(ResponseStatusException.class,()->service.confirmAndAssign(new AssignmentRequest(b.getBookingId(),d.getDriverId(),null),BigDecimal.TEN));
        jdbc.update("update driver set driver_status='DISABLED' where driver_id=?",d.getDriverId());
        assertThrows(ResponseStatusException.class,()->service.confirmAndAssign(request(b,d,10,0),BigDecimal.TEN));
        jdbc.update("update driver set driver_status='ACTIVE' where driver_id=?",d.getDriverId());
        b.setBookingPassengercount(20);bookings.saveAndFlush(b);
        assertThrows(ResponseStatusException.class,()->service.confirmAndAssign(request(b,d,10,0),BigDecimal.TEN));
        assertEquals(0,count(b));assertEquals("PENDING",bookings.findById(b.getBookingId()).orElseThrow().getBookingStatus());
    }
    @Test void databaseInsertFailureRollsBackEntireConfirmation() {
        Driver d=driver();Booking b=booking(9,0);
        jdbc.execute("alter table driver_assignment add constraint qa_block_new_assignment check (booking_id <> " + b.getBookingId() + ") not valid");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    ()->service.confirmAndAssign(request(b,d,10,0),new BigDecimal("100")));
        } finally { jdbc.execute("alter table driver_assignment drop constraint qa_block_new_assignment"); }
        Booking saved=bookings.findById(b.getBookingId()).orElseThrow();
        assertEquals("PENDING",saved.getBookingStatus());assertNull(saved.getDriverId());assertNull(saved.getVehicleId());assertNull(saved.getBookingTripfare());assertEquals(0,count(b));
    }
    @Test void databaseIndexRejectsDuplicateActiveAssignmentWithoutDeletingHistory() {
        Driver d=driver();Booking b=booking(9,0);
        service.confirmAndAssign(request(b,d,10,0),new BigDecimal("100"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update(
            "insert into driver_assignment(booking_id,driver_id,vehicle_id,assignment_status) select booking_id,driver_id,vehicle_id,'ASSIGNED' from driver_assignment where booking_id=?",b.getBookingId()));
        assertEquals(1,count(b));
    }
    @Test void concurrentOverlappingBookingsCannotReserveSamePair() throws Exception {
        Driver d=driver();Booking b=booking(9,0),other=booking(9,30);
        var gate=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        java.util.function.Function<Booking,Callable<Boolean>> action=booking->()->{gate.await();try{service.confirmAndAssign(request(booking,d,11,0),BigDecimal.TEN);return true;}catch(ResponseStatusException e){assertEquals(409,e.getStatusCode().value());return false;}};
        try {
            var a=executor.submit(action.apply(b));var c=executor.submit(action.apply(other));gate.countDown();
            assertNotEquals(a.get(30,TimeUnit.SECONDS),c.get(30,TimeUnit.SECONDS));
        } finally {executor.shutdownNow();}
        assertEquals(1,count(b)+count(other));
    }

    @Test void authenticationAndRequiredFieldsAreEnforced() throws Exception {
        mvc.perform(post("/api/bookings/1/confirm-and-assign").contentType("application/json").content("{\"driverId\":1,\"scheduledEndAt\":\"2028-01-01T10:00:00\"}"))
          .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings/1/confirm-and-assign").session(admin()).contentType("application/json").content("{}"))
          .andExpect(status().isBadRequest());
    }
    private java.net.http.HttpResponse<String> send(java.net.http.HttpClient client, String method,
            String path, String body) throws Exception {
        var builder=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+path))
                .header("Content-Type","application/json");
        builder.method(method,body == null ? java.net.http.HttpRequest.BodyPublishers.noBody() : java.net.http.HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(),java.net.http.HttpResponse.BodyHandlers.ofString());
    }
    private java.net.http.HttpClient authenticatedHttpClient() throws Exception {
        String email=UUID.randomUUID()+"@example.test";
        jdbc.update("insert into admin(admin_name,admin_email,admin_password) values ('HTTP QA',?,?)",email,passwords.encode("http-test-password"));
        var cookies=new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL);
        var client=java.net.http.HttpClient.newBuilder().cookieHandler(cookies).build();
        var login=send(client,"POST","/api/auth/login",json.writeValueAsString(java.util.Map.of("email",email,"password","http-test-password")));
        assertEquals(200,login.statusCode());
        return client;
    }
    @Test void actualHttpLoginConfirmAndFreshAssignmentListPersistTogether() throws Exception {
        var client=authenticatedHttpClient();Driver d=driver();Booking b=booking(9,0);
        String path="/api/bookings/"+b.getBookingId()+"/confirm-and-assign";
        String body=json.writeValueAsString(java.util.Map.of("driverId",d.getDriverId(),"scheduledEndAt","2028-01-01T10:00:00","tripFare",100));
        var result=send(client,"POST",path,body);assertEquals(200,result.statusCode(),result.body());
        long assignmentId=json.readTree(result.body()).get("assignmentId").asLong();
        var refresh=send(client,"GET","/api/assignments",null);assertEquals(200,refresh.statusCode());
        boolean found=false;
        for(var row:json.readTree(refresh.body())) if(row.get("assignmentId").asLong()==assignmentId) {
            found=true;assertEquals(b.getBookingId().longValue(),row.get("bookingId").asLong());
            assertEquals(d.getDriverId().longValue(),row.get("driverId").asLong());assertEquals("ASSIGNED",row.get("assignmentStatus").asText());
        }
        assertTrue(found);assertEquals("CONFIRMED",bookings.findById(b.getBookingId()).orElseThrow().getBookingStatus());
        assertEquals(409,send(client,"POST",path,body).statusCode());assertEquals(1,count(b));
    }
    @Test void actualHttpDistinguishesUnmappedRouteFromBookingAndDriverNotFound() throws Exception {
        var client=authenticatedHttpClient();
        var unmapped=send(client,"POST","/api/bookings/7/not-a-real-action","{}");
        assertEquals(404,unmapped.statusCode());assertEquals("ENDPOINT_NOT_FOUND",json.readTree(unmapped.body()).get("code").asText());
        String body="{\"driverId\":9223372036854775807,\"scheduledEndAt\":\"2028-01-01T10:00:00\",\"tripFare\":100}";
        var missingBooking=send(client,"POST","/api/bookings/9223372036854775807/confirm-and-assign",body);
        assertEquals(404,missingBooking.statusCode());assertEquals("Booking not found.",json.readTree(missingBooking.body()).get("message").asText());
        Booking b=booking(9,0);
        var missingDriver=send(client,"POST","/api/bookings/"+b.getBookingId()+"/confirm-and-assign",body);
        assertEquals(404,missingDriver.statusCode());assertEquals("Driver not found.",json.readTree(missingDriver.body()).get("message").asText());
        assertEquals("PENDING",bookings.findById(b.getBookingId()).orElseThrow().getBookingStatus());
    }

}
