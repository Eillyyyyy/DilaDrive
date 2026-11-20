package com.diladrive.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LocationGatewayServiceTest {
    HttpServer server;
    LocationGatewayService locations;
    AtomicInteger requests;
    String body;
    int status;
    @BeforeEach void setup() throws Exception {
        requests = new AtomicInteger(); status = 200;
        body = "[{\"display_name\":\"Fixture location, Malaysia\",\"lat\":\"3.139\",\"lon\":\"101.6869\"}]";
        server = HttpServer.create(new InetSocketAddress("localhost",0),0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            assertEquals("DilaDrive-test/1.0", exchange.getRequestHeaders().getFirst("User-Agent"));
            assertTrue(exchange.getRequestURI().getRawQuery().contains("format=jsonv2"));
            byte[] response = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,response.length);
            exchange.getResponseBody().write(response);exchange.close();
        });
        server.start();
        locations = new LocationGatewayService("http://localhost:"+server.getAddress().getPort(),
                "DilaDrive-test/1.0","my",new ObjectMapper());
    }
    @AfterEach void close() { server.stop(0); }
    @Test void searchNormalizesAndCachesResults() {
        var first = locations.search("  Fixture   address  ");
        assertEquals(first,locations.search("Fixture address"));
        assertEquals(1, requests.get());
        assertEquals(3.139,first.get(0).latitude());
        assertEquals("Fixture location, Malaysia",first.get(0).address());
    }
    @Test void emptySearchRemainsEmptyAndIsCached() {
        body="[]";assertTrue(locations.search("Unknown landmark").isEmpty());
        assertTrue(locations.search("Unknown landmark").isEmpty());assertEquals(1,requests.get());
    }
    @Test void searchAndReverseShareOneRateLimit() {
        locations.search("Fixture address");
        var failure=assertThrows(ResponseStatusException.class,()->locations.reverse(3,101));
        assertEquals(429,failure.getStatusCode().value());assertEquals(1,requests.get());
    }
    @Test void invalidProviderCoordinatesAreNotReturned() {
        body="[{\"display_name\":\"Invalid location\",\"lat\":\"999\",\"lon\":\"101\"}]";
        assertEquals(502,assertThrows(ResponseStatusException.class,()->locations.search("Fixture address")).getStatusCode().value());
    }
    @Test void reverseReturnsActualProviderAddressAndCoordinates() {
        body="{\"display_name\":\"Fixture reverse address\",\"lat\":3.1,\"lon\":101.1}";
        var result=locations.reverse(3.1,101.1);
        assertEquals("Fixture reverse address",result.address());assertEquals(101.1,result.longitude());
    }
    @Test void invalidInputNeverReachesProvider() {
        assertThrows(ResponseStatusException.class,()->locations.search("ab"));
        assertThrows(ResponseStatusException.class,()->locations.reverse(Double.NaN,101));
        assertThrows(ResponseStatusException.class,()->locations.reverse(91,101));assertEquals(0,requests.get());
    }
    @Test void providerFailuresHaveNoInventedFallback() {
        status=503;
        assertEquals(502,assertThrows(ResponseStatusException.class,()->locations.search("Fixture address")).getStatusCode().value());
    }
    @Test void upstreamRateLimitEnforcesBackoff() {
        status=429;
        assertEquals(429,assertThrows(ResponseStatusException.class,()->locations.search("Fixture address")).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->locations.search("Another address"));assertEquals(1,requests.get());
    }
    @Test void missingReverseAddressDoesNotInventCoordinates() {
        body="{\"error\":\"Unable to geocode\"}";
        assertEquals(404,assertThrows(ResponseStatusException.class,()->locations.reverse(0,0)).getStatusCode().value());
    }
}
