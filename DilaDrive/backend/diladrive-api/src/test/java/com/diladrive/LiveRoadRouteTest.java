package com.diladrive;

import com.diladrive.service.*;
import com.diladrive.dto.TripQuoteRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.core.env.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.net.*;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real authenticated HTTP endpoint + public OSRM, fixtures only in a disposable schema. */
@EnabledIfEnvironmentVariable(named="RUN_LIVE_ROAD_ROUTE", matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.profiles.active=local", "spring.jpa.show-sql=false", "logging.level.root=ERROR",
    "booking.push-enabled=false", "booking.routing-url=https://router.project-osrm.org"})
class LiveRoadRouteTest {
    static String schema="diladrive_route_check_"+System.currentTimeMillis();
    static String url,user,password;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        if (System.getenv("LIVE_ROAD_ROUTE_PORT") != null)
            registry.add("server.port", () -> System.getenv("LIVE_ROAD_ROUTE_PORT"));
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
        if(url!=null && schema.matches("diladrive_route_check_[0-9]+"))
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()) {s.execute("drop schema "+schema+" cascade");}
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordService passwords;
    @Autowired ObjectMapper json;
    @Autowired LocationGatewayService locations;

    @Test void uitmToKliaThroughAuthenticatedHttpEndpoint() throws Exception {
        assertEquals(schema,jdbc.queryForObject("select current_schema()",String.class));
        String secret=UUID.randomUUID().toString();
        jdbc.update("insert into customer(cust_name,cust_email,cust_password,cust_status) values(?,?,?,?)",
            "Route verification", "route-check@example.invalid", passwords.encode(secret), "ACTIVE");
        var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        var client=HttpClient.newBuilder().cookieHandler(cookies).build();
        String base="http://127.0.0.1:"+port;
        var login=client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/mobile/login"))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                Map.of("email","route-check@example.invalid","password",secret,"role","CUSTOMER")))).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,login.statusCode(),"Real customer login must succeed");
        var pickup=locations.search("Universiti Teknologi MARA Shah Alam").stream().findFirst().orElseThrow();
        var destination=locations.search("KLIA Terminal 1").stream().findFirst().orElseThrow();
        var trip=new TripQuoteRequest(pickup.address(),destination.address(),pickup.latitude(),pickup.longitude(),
            destination.latitude(),destination.longitude(),LocalDateTime.now(ZoneId.of("Asia/Kuala_Lumpur")).plusDays(1),
            1,0,0,0,"NORMAL_TRANSFER",null,null);
        var response=client.send(HttpRequest.newBuilder(URI.create(base+"/api/bookings/route"))
            .timeout(Duration.ofSeconds(30)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(trip))).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode(),response.body());
        var body=json.readTree(response.body());
        assertTrue(body.path("distanceKm").asDouble()>0);
        assertTrue(body.path("durationSeconds").asLong()>0);
        assertTrue(body.path("geometry").size()>2);
        for(var p:body.path("geometry")) {
            assertTrue(p.get(0).asDouble()>=-180 && p.get(0).asDouble()<=180);
            assertTrue(p.get(1).asDouble()>=-90 && p.get(1).asDouble()<=90);
        }
        Files.writeString(Path.of("target/live-road-route-response.json"),response.body());
        Files.writeString(Path.of("target/live-road-route-request.json"),json.writeValueAsString(trip));
        System.out.printf("LIVE HTTP %d: %s -> %s; %.3f km, %d seconds, %d points; port %d%n",
            response.statusCode(),pickup.address(),destination.address(),body.path("distanceKm").asDouble(),
            body.path("durationSeconds").asLong(),body.path("geometry").size(),port);
        var preflight=client.send(HttpRequest.newBuilder(URI.create(base+"/api/bookings/route"))
            .header("Origin","http://127.0.0.1:54321").header("Access-Control-Request-Method","POST")
            .method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals("http://127.0.0.1:54321",preflight.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        var denied=client.send(HttpRequest.newBuilder(URI.create(base+"/api/bookings/route"))
            .header("Origin","https://untrusted.example").header("Access-Control-Request-Method","POST")
            .method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,denied.statusCode());
        var invalid=json.valueToTree(trip);
        ((com.fasterxml.jackson.databind.node.ObjectNode)invalid).put("pickupLatitude",91);
        var rejected=client.send(HttpRequest.newBuilder(URI.create(base+"/api/bookings/route"))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(invalid)))
            .build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(400,rejected.statusCode());
    }
}
