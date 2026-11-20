package com.diladrive.service;
import com.diladrive.dto.TripQuoteRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class RoadRoutingServiceTest {
    TripQuoteRequest request() {return new TripQuoteRequest("Pickup","Destination",3.0,101.0,4.0,102.0,LocalDateTime.now().plusDays(1),1,0,0,0,"NORMAL_TRANSFER",null,null);}
    @Test void parsesRoadDistanceAndDurationAndUsesDrivingCoordinates() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("localhost",0),0);
        server.createContext("/route/v1/driving/",exchange->{
            assertTrue(exchange.getRequestURI().toString().contains("101.0,3.0;102.0,4.0"));
            byte[] body="{\"code\":\"Ok\",\"routes\":[{\"distance\":58000,\"duration\":7200,\"geometry\":{\"coordinates\":[[101,3],[102,4]]}}]}".getBytes();
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try {
            var route=new RoadRoutingService("http://localhost:"+server.getAddress().getPort(),new ObjectMapper()).route(request());
            assertEquals(58,route.distanceKm());assertEquals(7200,route.durationSeconds());assertEquals(2,route.geometry().size());
        } finally {server.stop(0);}
    }
    @Test void missingConfigurationHasNoFallback() {
        var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new RoadRoutingService("",new ObjectMapper()).route(request()));
        assertEquals(503,error.getStatusCode().value());
    }
    @Test void invalidCoordinatesRejectedBeforeProviderRequest() {
        var r=request();
        var invalid=new TripQuoteRequest(r.pickupAddress(),r.destinationAddress(),Double.NaN,101.0,4.0,102.0,r.scheduledAt(),1,0,0,0,"NORMAL_TRANSFER",null,null);
        var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new RoadRoutingService("http://localhost:1",new ObjectMapper()).route(invalid));
        assertEquals(400,error.getStatusCode().value());
    }
    @Test void providerUnavailableIsBadGateway() throws Exception { assertProviderError(503,"{}",502); }
    @Test void providerAuthenticationFailureIsBadGateway() throws Exception { assertProviderError(401,"{}",502); }
    @Test void malformedGeometryCannotEnableContinue() throws Exception {
        assertProviderError(200,"{\"code\":\"Ok\",\"routes\":[{\"distance\":100,\"duration\":20,\"geometry\":{\"coordinates\":[[999,3],[102,4]]}}]}",502);
    }
    @Test void providerTimeoutIsGatewayTimeout() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("localhost",0),0);
        server.createContext("/",exchange->{try {Thread.sleep(300);} catch(InterruptedException ignored) {Thread.currentThread().interrupt();} exchange.close();});
        server.start();
        try {
            var router=new RoadRoutingService("http://localhost:"+server.getAddress().getPort(),new ObjectMapper(),java.time.Duration.ofMillis(50));
            var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->router.route(request()));
            assertEquals(504,error.getStatusCode().value());
        } finally {server.stop(0);}
    }
    void assertProviderError(int status,String payload,int expected) throws Exception {
        var server=HttpServer.create(new InetSocketAddress("localhost",0),0);
        server.createContext("/",exchange->{byte[] body=payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close();});
        server.start();
        try {
            var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new RoadRoutingService("http://localhost:"+server.getAddress().getPort(),new ObjectMapper()).route(request()));
            assertEquals(expected,error.getStatusCode().value());
        } finally {server.stop(0);}
    }
    @Test void noRouteHasNoFallback() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("localhost",0),0);
        server.createContext("/",exchange->{byte[] body="{\"code\":\"NoRoute\"}".getBytes();exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        try {assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new RoadRoutingService("http://localhost:"+server.getAddress().getPort(),new ObjectMapper()).route(request()));}
        finally {server.stop(0);}
    }
}
