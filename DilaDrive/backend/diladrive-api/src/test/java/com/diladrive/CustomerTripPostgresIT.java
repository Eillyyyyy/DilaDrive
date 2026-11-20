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

/** All fixtures and migrations run in a unique disposable PostgreSQL schema. */
@SpringBootTest(properties={"spring.jpa.show-sql=false","logging.level.root=ERROR","booking.push-enabled=false"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="RUN_CUSTOMER_TRIP_IT", matches="true")
class CustomerTripPostgresIT {
    static String schema="diladrive_trip_check_"+System.currentTimeMillis();
    static String url,user,password;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        var properties=new Properties();
        try(var input=Files.newInputStream(Path.of("src/main/resources/application.properties"))) { properties.load(input); }
        var env=new StandardEnvironment();
        env.getPropertySources().addLast(new PropertiesPropertySource("application",properties));
        url=env.getProperty("spring.datasource.url"); user=env.getProperty("spring.datasource.username"); password=env.getProperty("spring.datasource.password");
        try(var c=DriverManager.getConnection(url,user,password); var s=c.createStatement()) {
            s.execute("create schema "+schema); s.execute("set search_path to "+schema);
            for(String file:List.of("BASELINE_CURRENT.sql","020_one_driver_one_vehicle.sql","021_driver_profile_image.sql","022_active_booking_assignment.sql","023_customer_booking_flow.sql","024_pending_without_reservation.sql","025_distance_pricing.sql","026_trip_charges_and_commission.sql"))
                s.execute(Files.readString(Path.of("../../database/schema/"+file)));
        }
        registry.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+schema);
        registry.add("spring.datasource.username",()->user); registry.add("spring.datasource.password",()->password);
    }
    @AfterAll static void cleanup() throws Exception {
        if(url!=null && schema.matches("diladrive_trip_check_[0-9]+"))
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()) {s.execute("drop schema "+schema+" cascade");}
    }
    @Autowired CustomerTripService trips;
    @Autowired BookingService bookings;
    @Autowired AssignmentServiceImpl assignments;
    @Autowired TripFinanceService finance;
    @Autowired PricingService pricing;
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
    @Test void excessivePassengersExcluded() { assertFalse(trips.quote(request(5,0,0,0)).available()); }
    @Test void excessiveLuggageExcluded() { assertFalse(trips.quote(request(1,0,0,2)).available()); }
    @Test void maintenanceExcluded() { jdbc.update("update vehicle set vehicle_status='MAINTENANCE'");assertFalse(trips.quote(request(1,0,0,0)).available()); }
    @Test void maintenancePeriodExcluded() {
        jdbc.update("insert into vehicle_unavailable_period(vehicle_id,start_at,end_at) values(?,?,?)",vehicle,start.minusHours(1),start.plusHours(1));
        assertFalse(trips.quote(request(1,0,0,0)).available());
    }
    @Test void pendingRequestsNeverHoldAndCancellationKeepsAvailability() {
        var b=create(); assertTrue(trips.quote(request(3,1,1,0)).available());
        bookings.cancelCustomerBooking(customer,((Number)b.get("bookingId")).longValue()); assertTrue(trips.quote(request(3,1,1,0)).available());
        create(); assertEquals(0,jdbc.queryForObject("select count(*) from booking_vehicle_hold",Integer.class));
        assertTrue(trips.quote(request(3,1,1,0)).available());
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
        assertEquals("Sedan",trips.quote(request(3,1,1,0)).vehicle().get("vehicleType")); create();
        assertEquals(2,jdbc.queryForObject("select count(*) from booking",Integer.class));
    }
    @Test void concurrentPendingRequestsDoNotReserveSameVehicle() throws Exception {
        var executor=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            Callable<Boolean> task=()->{gate.await();try {create();return true;}catch(org.springframework.web.server.ResponseStatusException e){assertEquals(409,e.getStatusCode().value());return false;}};
            var a=executor.submit(task);var b=executor.submit(task);gate.countDown();
            assertTrue(a.get(20,TimeUnit.SECONDS)); assertTrue(b.get(20,TimeUnit.SECONDS));
            assertEquals(2,jdbc.queryForObject("select count(*) from booking",Integer.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from booking_vehicle_hold",Integer.class));
        } finally {executor.shutdownNow();}
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
    @Test void editRefreshesSavedFareAndScheduleWithoutHold() {
        long id=((Number)create().get("bookingId")).longValue();
        assertTrue(trips.quoteEdit(customer,id,request(3,1,1,0)).available());
        start=start.plusDays(1);
        when(routing.route(any())).thenReturn(new RoadRoutingService.Route(70,9000,List.of(List.of(101.0,3.0),List.of(102.0,4.0))));
        trips.update(customer,id,approved(request(3,1,1,0)));
        assertEquals(new java.math.BigDecimal("140.00"),jdbc.queryForObject("select booking_tripfare from booking where booking_id=?",java.math.BigDecimal.class,id));
        assertEquals(start.plusSeconds(9000).plusMinutes(30),jdbc.queryForObject("select reserved_end_at from booking where booking_id=?",LocalDateTime.class,id));
        assertEquals(0,jdbc.queryForObject("select count(*) from booking_vehicle_hold",Integer.class));
        assertEquals(1,jdbc.queryForObject("select count(*) from booking",Integer.class));
    }
    @Test void unverifiedCustomerCannotCreate() {
        jdbc.update("update customer set cust_status='PENDING'");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,this::create);
        assertEquals(0,jdbc.queryForObject("select count(*) from booking",Integer.class));
    }
    @Test void twoCustomersCanRequestSameSlotThenAdminConfirmsAlternativeAndHistoryIsPreserved() throws Exception {
        long first=((Number)create().get("bookingId")).longValue();
        long otherCustomer=jdbc.queryForObject("insert into customer(cust_name,cust_phoneno,cust_status) values('Second Customer','0199999999','ACTIVE') returning cust_id",Long.class);
        long second=((Number)trips.create(otherCustomer,approved(request(3,1,1,0)),UUID.randomUUID().toString()).get("bookingId")).longValue();
        assertEquals(2,jdbc.queryForObject("select count(*) from booking where booking_status='PENDING'",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from driver_assignment",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from booking_vehicle_hold",Integer.class));
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
        assertTrue(assignments.options(second,null).isEmpty());
        long alternative=addVehicle("Van",8,10);
        long alternativeVehicle=jdbc.queryForObject("select vehicle_id from vehicle where driver_id=?",Long.class,alternative);
        assignments.confirmAndAssign(new AssignmentRequest(second,alternative,alternativeVehicle,start.plusHours(3)),null);
        var adminList=assignments.getAllAssignments(); assertEquals(2,adminList.size());
        assertEquals(1,assignments.getDriverAssignments(driver).size());
        assertEquals("CONFIRMED",bookings.getCustomerBookings(otherCustomer).get(0).status());
        assertEquals(3,assignments.getDriverAssignments(driver).get(0).luggageUnits());
        long secondAssignment=adminList.stream().filter(a->a.bookingId().equals(second)).findFirst().orElseThrow().assignmentId();
        assignments.deleteAssignment(secondAssignment);
        assertEquals("CANCELLED",jdbc.queryForObject("select booking_status from booking where booking_id=?",String.class,second));
        long firstAssignment=adminList.stream().filter(a->a.bookingId().equals(first)).findFirst().orElseThrow().assignmentId();
        var replacement=assignments.reassign(firstAssignment,new AssignmentRequest(first,alternative,alternativeVehicle,start.plusHours(3)));
        assertNotEquals(firstAssignment,replacement.assignmentId());
        assertEquals("SUPERSEDED",jdbc.queryForObject("select assignment_status from driver_assignment where assignment_id=?",String.class,firstAssignment));
        assertEquals(driver,jdbc.queryForObject("select driver_id from driver_assignment where assignment_id=?",Long.class,firstAssignment));
        assertEquals(3,jdbc.queryForObject("select count(*) from driver_assignment",Integer.class));
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
        long first=((Number)create().get("bookingId")).longValue(), second=((Number)create().get("bookingId")).longValue();
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            java.util.function.Function<Long,Callable<Boolean>> confirm=id->()->{gate.await();try {
                assignments.confirmAndAssign(new AssignmentRequest(id,driver,vehicle,start.plusHours(3)),null);return true;
            } catch(org.springframework.web.server.ResponseStatusException e) {assertEquals(409,e.getStatusCode().value());return false;}};
            var a=pool.submit(confirm.apply(first));var b=pool.submit(confirm.apply(second));gate.countDown();
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("select count(*) from driver_assignment",Integer.class));
            assertEquals(1,jdbc.queryForObject("select count(*) from booking where booking_status='PENDING'",Integer.class));
        } finally {pool.shutdownNow();}
    }
    @Test void ancientHoldsAndPendingAssignmentRowsDoNotOccupyAvailability() {
        long id=((Number)create().get("bookingId")).longValue();
        jdbc.update("insert into booking_vehicle_hold values(?,?,?,?,?)",id,vehicle,start,start.plusHours(3),start.plusDays(1));
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
        assignments.completeAssignment(assigned.assignmentId());
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
        assertEquals(CustomerTripService.AvailabilityStatus.CAPACITY_UNAVAILABLE,trips.quote(request(5,0,0,0)).availabilityStatus());
        assertEquals(CustomerTripService.AvailabilityStatus.CAPACITY_UNAVAILABLE,trips.quote(request(1,0,0,2)).availabilityStatus());
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
        long pending=((Number)create().get("bookingId")).longValue();
        var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,
            ()->assignments.confirmAndAssign(new AssignmentRequest(pending,driver,vehicle,start.plusHours(3)),null));
        assertEquals(409,error.getStatusCode().value());
        assertTrue(assignments.options(pending,null).isEmpty());
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
