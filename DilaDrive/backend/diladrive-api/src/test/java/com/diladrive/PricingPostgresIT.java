package com.diladrive;

import com.diladrive.service.PasswordService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.core.env.*;
import java.math.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP, PostgreSQL, authentication and OSRM. All fixtures stay in a disposable schema. */
@EnabledIfEnvironmentVariable(named="RUN_PRICING_IT",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.jpa.show-sql=false","logging.level.root=WARN","booking.push-enabled=false",
        "booking.pickup-check-enabled=false","booking.reservation-sweep-ms=3600000"})
class PricingPostgresIT {
    static final String schema="diladrive_pricing_check_"+System.currentTimeMillis();
    static String url,user,password;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        var properties=new Properties();try(var input=Files.newInputStream(Path.of("src/main/resources/application.properties"))) {properties.load(input);}
        var env=new StandardEnvironment();env.getPropertySources().addLast(new PropertiesPropertySource("application",properties));
        url=env.getProperty("spring.datasource.url");user=env.getProperty("spring.datasource.username");password=env.getProperty("spring.datasource.password");
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()) {
            s.execute("create schema "+schema);s.execute("set search_path to "+schema);
            for(String file:List.of("BASELINE_CURRENT.sql","020_one_driver_one_vehicle.sql","021_driver_profile_image.sql","022_active_booking_assignment.sql","023_customer_booking_flow.sql","024_pending_without_reservation.sql","025_distance_pricing.sql","026_trip_charges_and_commission.sql","028_customer_account_verification.sql"))
                s.execute(Files.readString(Path.of("../../database/schema/"+file)));
        }
        registry.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+schema);
        registry.add("spring.datasource.username",()->user);registry.add("spring.datasource.password",()->password);
    }
    @AfterAll static void cleanup() throws Exception {
        if(url!=null && schema.matches("diladrive_pricing_check_[0-9]+"))
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()) {s.execute("drop schema "+schema+" cascade");}
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordService passwords;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    HttpClient admin,customer,driver;
    long vehicleId,driverId,otherVehicle,otherDriver;
    @BeforeEach void fixture() throws Exception {
        assertEquals(schema,jdbc.queryForObject("select current_schema()",String.class));
        jdbc.execute("truncate admin,booking_fare_revision,booking_request_idempotency,notification,documents,payment,driver_assignment,booking,vehicle_unavailable_period,driver_unavailable_period,vehicle,driver,customer,booking_fare_rate restart identity cascade");
        jdbc.update("insert into trip_pricing_settings(id,rate_per_km,hourly_rate,commission_percent,commission_basis,partial_hour_policy,active,use_category_rates) values(1,2,60,15,'BASE_AND_HOURS','PRORATE',true,true)");
        String secret=UUID.randomUUID().toString(),encoded=passwords.encode(secret);
        jdbc.update("insert into admin(admin_name,admin_email,admin_password) values('Pricing test Admin','pricing-admin@example.invalid',?)",encoded);
        jdbc.update("insert into customer(cust_name,cust_email,cust_phoneno,cust_status,cust_type,cust_password) values('Pricing test Customer','pricing-customer@example.invalid','0123456789','ACTIVE','COMPANY',?)",encoded);
        driverId=vehicle("SEDAN",4,encoded);otherDriver=vehicle("MPV",7,encoded);
        vehicleId=jdbc.queryForObject("select vehicle_id from vehicle where driver_id=?",Long.class,driverId);
        otherVehicle=jdbc.queryForObject("select vehicle_id from vehicle where driver_id=?",Long.class,otherDriver);
        admin=client();customer=client();driver=client();
        assertEquals(200,send(admin,"POST","/api/auth/login",Map.of("email","pricing-admin@example.invalid","password",secret),null).statusCode());
        assertEquals(200,send(customer,"POST","/api/auth/mobile/login",Map.of("email","pricing-customer@example.invalid","password",secret,"role","CUSTOMER"),null).statusCode());
        assertEquals(200,send(driver,"POST","/api/auth/mobile/login",Map.of("email","pricing-driver-SEDAN@example.invalid","password",secret,"role","DRIVER"),null).statusCode());
    }
    long vehicle(String category,int seats,String encoded) {
        return new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> {
        Long d=jdbc.queryForObject("insert into driver(driver_name,driver_email,driver_password,driver_status,driver_availability) values('Pricing test Driver',?,?,'ACTIVE','AVAILABLE') returning driver_id",Long.class,"pricing-driver-"+category+"@example.invalid",encoded);
        jdbc.update("insert into vehicle(driver_id,vehicle_type,vehicle_model,vehicle_capacity,vehicle_luggagecapacity,vehicle_status,vehicle_plateno) values(?,?,?,?,?,'AVAILABLE',?)",d,category,"Fixture vehicle",seats,6,"TEST-"+d);
        return d;
        });
    }
    HttpClient client() {return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
    HttpResponse<String> send(HttpClient client,String method,String path,Object body,String key) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
        if(key!=null)request.header("Idempotency-Key",key);
        return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    ObjectNode trip() {
        // Coordinates are actual Nominatim results verified for these named landmarks.
        return json.valueToTree(Map.ofEntries(Map.entry("pickupAddress","UiTM Kampus Jasin"),Map.entry("destinationAddress","KLIA Terminal 1 Main Building"),
                Map.entry("pickupLatitude",2.2254670),Map.entry("pickupLongitude",102.4556074),Map.entry("destinationLatitude",2.7527291),Map.entry("destinationLongitude",101.7055234),
                Map.entry("scheduledAt","2035-02-01T10:00:00"),Map.entry("passengerCount",3),Map.entry("smallCount",1),Map.entry("mediumCount",0),Map.entry("largeCount",0),Map.entry("tripType","NORMAL_TRANSFER")));
    }
    JsonNode body(HttpResponse<String> response,int expected) throws Exception {assertEquals(expected,response.statusCode(),response.body());return json.readTree(response.body());}
    void rate(String category,String base,String perKm) throws Exception {
        body(send(admin,"POST","/api/admin/pricing",Map.of("vehicleCategory",category,"baseFare",base,"ratePerKm",perKm,"active",true),null),201);
    }
    ObjectNode accepted() throws Exception {
        var request=trip();var quote=body(send(customer,"POST","/api/bookings/estimate",request,null),200);
        request.put("vehicleCategory",quote.path("recommendedVehicleType").asText());request.put("acceptedQuoteToken",quote.path("quoteToken").asText());
        return request;
    }
    long create(ObjectNode request,String key) throws Exception {return body(send(customer,"POST","/api/bookings/customer/complete",request,key),201).path("bookingId").asLong();}
    @Test void availabilityIndependentOfMissingAndInactivePricing() throws Exception {
        var availability=body(send(customer,"POST","/api/bookings/availability",trip(),null),200);
        assertEquals("AVAILABLE",availability.path("availabilityStatus").asText());assertTrue(availability.path("totalFare").isNull());
        var fallback=body(send(customer,"POST","/api/bookings/estimate",trip(),null),200);assertEquals(0,new BigDecimal("2").compareTo(fallback.path("ratePerKm").decimalValue()));
        rate("SEDAN","17.35","1.2345");body(send(admin,"PATCH","/api/admin/pricing/SEDAN/status",Map.of("active",false),null),200);
        assertEquals("AVAILABLE",body(send(customer,"POST","/api/bookings/availability",trip(),null),200).path("availabilityStatus").asText());
        assertEquals("PRICING_NOT_CONFIGURED",body(send(customer,"POST","/api/bookings/estimate",trip(),null),422).path("code").asText());
    }
    @Test void adminCrudValidationAndCategoryNormalization() throws Exception {
        rate("sedan","17.35","1.2345");
        body(send(admin,"POST","/api/admin/pricing",Map.of("vehicleCategory","SEDAN","baseFare","1","ratePerKm","1","active",true),null),409);
        var update=body(send(admin,"PUT","/api/admin/pricing/SEDAN",Map.of("vehicleCategory","SEDAN","baseFare","18.35","ratePerKm","1.2345","active",true),null),200);
        assertEquals(new BigDecimal("18.35"),update.path("baseFare").decimalValue());
        assertEquals(1,jdbc.queryForObject("select count(*) from booking_fare_rate",Integer.class));
        body(send(admin,"POST","/api/admin/pricing",Map.of("vehicleCategory","UNKNOWN","baseFare","1","ratePerKm","1","active",true),null),400);
        body(send(admin,"PUT","/api/admin/pricing/SEDAN",Map.of("vehicleCategory","SEDAN","baseFare","-1","ratePerKm","1","active",true),null),400);
        assertEquals(401,send(customer,"GET","/api/admin/pricing",null,null).statusCode());
        assertEquals(401,send(driver,"PATCH","/api/admin/pricing/SEDAN/status",Map.of("active",false),null).statusCode());
    }
    @Test void realRoadFareAndPendingSnapshotWithReservation() throws Exception {
        rate("SEDAN","17.35","1.2345");
        var quote=body(send(customer,"POST","/api/bookings/estimate",trip(),null),200);
        assertTrue(quote.path("geometry").size()>2);assertTrue(quote.path("durationSeconds").asLong()>0);
        var expected=quote.path("estimatedDistanceKm").decimalValue().multiply(new BigDecimal("1.2345")).setScale(2,RoundingMode.HALF_UP);
        assertEquals(0,expected.compareTo(quote.path("totalFare").decimalValue()));
        var request=trip();request.put("vehicleCategory","SEDAN");request.put("acceptedQuoteToken",quote.path("quoteToken").asText());
        String key=UUID.randomUUID().toString();long id=create(request,key);assertEquals(id,create(request,key));
        assertEquals("PENDING",jdbc.queryForObject("select booking_status from booking where booking_id=?",String.class,id));
        assertEquals(0,jdbc.queryForObject("select count(*) from booking where vehicle_id is not null or driver_id is not null",Integer.class));
        assertEquals(1,jdbc.queryForObject("select count(*) from booking_vehicle_hold where driver_id is not null",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from driver_assignment",Integer.class));
        String snapshot=jdbc.queryForObject("select fare_snapshot from booking where booking_id=?",String.class,id);assertTrue(json.readTree(snapshot).hasNonNull("quoteToken"));
        body(send(admin,"PUT","/api/admin/pricing/SEDAN",Map.of("vehicleCategory","SEDAN","baseFare","19.35","ratePerKm","1.3333","active",true),null),200);
        assertEquals(0,expected.compareTo(jdbc.queryForObject("select booking_tripfare from booking where booking_id=?",BigDecimal.class,id)));
        assertEquals(snapshot,jdbc.queryForObject("select fare_snapshot from booking where booking_id=?",String.class,id));
        Files.writeString(Path.of("target/pricing-real-api-estimate.json"),json.writeValueAsString(quote));
        System.out.printf("REAL PRICING API port=%d schema=%s distance=%s km fare=%s MYR geometry=%d%n",port,schema,quote.path("estimatedDistanceKm"),quote.path("totalFare"),quote.path("geometry").size());
    }
    @Test void fareChangesRequireReviewBeforeAnyBookingIsSaved() throws Exception {
        rate("SEDAN","17.35","1.2345");var request=accepted();
        body(send(admin,"PUT","/api/admin/pricing/SEDAN",Map.of("vehicleCategory","SEDAN","baseFare","19.35","ratePerKm","1.3333","active",true),null),200);
        String key=UUID.randomUUID().toString();var refreshed=body(send(customer,"POST","/api/bookings/customer/complete",request,key),409);
        assertEquals("FARE_REVIEW_REQUIRED",refreshed.path("code").asText());assertEquals(0,jdbc.queryForObject("select count(*) from booking",Integer.class));
        request.put("acceptedQuoteToken",refreshed.path("refreshedQuote").path("quoteToken").asText());create(request,key);
    }
    @Test void submissionRechecksPhysicalAvailability() throws Exception {
        rate("SEDAN","17.35","1.2345");var request=accepted();jdbc.update("update vehicle set vehicle_status='MAINTENANCE'");
        body(send(customer,"POST","/api/bookings/customer/complete",request,UUID.randomUUID().toString()),409);
        assertEquals(0,jdbc.queryForObject("select count(*) from booking",Integer.class));
        assertEquals("SCHEDULE_UNAVAILABLE",body(send(customer,"POST","/api/bookings/availability",trip(),null),200).path("availabilityStatus").asText());
    }
    @Test void categoryChangeRequiresCustomerAcceptanceAndPreservesHistory() throws Exception {
        rate("SEDAN","17.35","1.2345");rate("MPV","24.85","1.7654");long id=create(accepted(),UUID.randomUUID().toString());
        BigDecimal original=jdbc.queryForObject("select booking_tripfare from booking where booking_id=?",BigDecimal.class,id);
        var assignment=Map.of("driverId",otherDriver,"vehicleId",otherVehicle,"scheduledEndAt","2035-02-02T10:00:00");
        assertEquals("CUSTOMER_ACCEPTANCE_REQUIRED",body(send(admin,"POST","/api/bookings/"+id+"/confirm-and-assign",assignment,null),409).path("code").asText());
        var proposal=body(send(admin,"POST","/api/bookings/"+id+"/fare-proposals",Map.of("vehicleCategory","MPV"),null),200);
        assertEquals(0,original.compareTo(jdbc.queryForObject("select booking_tripfare from booking where booking_id=?",BigDecimal.class,id)));
        body(send(customer,"POST","/api/bookings/"+id+"/fare-proposals/"+proposal.path("proposalId").asLong()+"/accept",null,null),200);
        body(send(admin,"POST","/api/bookings/"+id+"/confirm-and-assign",assignment,null),200);
        assertEquals("CONFIRMED",jdbc.queryForObject("select booking_status from booking where booking_id=?",String.class,id));
        assertEquals("ACCEPTED",jdbc.queryForObject("select status from booking_fare_revision where booking_id=?",String.class,id));
        assertEquals(0,original.compareTo(jdbc.queryForObject("select original_fare from booking_fare_revision where booking_id=?",BigDecimal.class,id)));
        body(send(admin,"POST","/api/bookings/"+id+"/confirm-and-assign",assignment,null),409);
        assertEquals(1,jdbc.queryForObject("select count(*) from driver_assignment",Integer.class));
    }
    @Test void validZeroRatesRemainValidThroughConfirmationAndDocuments() throws Exception {
        rate("SEDAN","0.00","0.0000");long id=create(accepted(),UUID.randomUUID().toString());
        body(send(admin,"POST","/api/bookings/"+id+"/confirm-and-assign",Map.of("driverId",driverId,"vehicleId",vehicleId,"scheduledEndAt","2035-02-02T10:00:00"),null),200);
        var quotation=body(send(admin,"POST","/api/documents/quotation",Map.of("bookingId",id,"amount","0.00"),null),201);
        assertEquals(0,quotation.path("amount").decimalValue().signum());
        jdbc.update("update booking set booking_status='COMPLETED' where booking_id=?",id);
        assertEquals(0,body(send(admin,"POST","/api/documents/"+id+"/generate-invoice",Map.of("sourceType","BOOKING"),null),201).path("amount").decimalValue().signum());
    }
    @Test void sameCategoryAssignmentAndDocumentsKeepAcceptedFareAfterRateUpdate() throws Exception {
        rate("SEDAN","17.35","1.2345");long id=create(accepted(),UUID.randomUUID().toString());
        BigDecimal original=jdbc.queryForObject("select booking_tripfare from booking where booking_id=?",BigDecimal.class,id);
        body(send(admin,"PUT","/api/admin/pricing/SEDAN",Map.of("vehicleCategory","SEDAN","baseFare","19.35","ratePerKm","1.3333","active",true),null),200);
        body(send(admin,"POST","/api/bookings/"+id+"/confirm-and-assign",Map.of("driverId",driverId,"vehicleId",vehicleId,"scheduledEndAt","2035-02-02T10:00:00"),null),200);
        assertEquals(0,original.compareTo(jdbc.queryForObject("select booking_tripfare from booking where booking_id=?",BigDecimal.class,id)));
        var quotation=body(send(admin,"POST","/api/documents/quotation",Map.of("bookingId",id,"amount","999.99"),null),201);
        assertEquals(0,original.compareTo(quotation.path("amount").decimalValue()));
        jdbc.update("update booking set booking_status='COMPLETED' where booking_id=?",id);
        var invoice=body(send(admin,"POST","/api/documents/"+id+"/generate-invoice",Map.of("sourceType","BOOKING"),null),201);
        assertEquals(0,original.compareTo(invoice.path("amount").decimalValue()));
    }
}
