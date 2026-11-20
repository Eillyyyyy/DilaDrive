package com.diladrive.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LocationAutocompleteServiceTest {
    HttpServer server;LocationAutocompleteService service;AtomicInteger requests;String body;int status;
    @BeforeEach void setup() throws Exception {
        requests=new AtomicInteger();status=200;
        body="{\"features\":[{\"properties\":{\"name\":\"KLIA fixture\",\"city\":\"Sepang\",\"country\":\"Malaysia\"},\"geometry\":{\"coordinates\":[101.7,2.7]}}]}";
        server=HttpServer.create(new InetSocketAddress("localhost",0),0);
        server.createContext("/api/",exchange->{
            requests.incrementAndGet();assertTrue(exchange.getRequestURI().getRawQuery().contains("countrycode=MY"));
            byte[] response=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        service=new LocationAutocompleteService("http://localhost:"+server.getAddress().getPort(),"DilaDrive-test/1.0","my",new ObjectMapper());
    }
    @AfterEach void close(){server.stop(0);}
    @Test void acceptsKLIAAndConvertsGeoJsonLongitudeLatitude() {
        var result=service.suggest("KLIA").get(0);
        assertEquals("KLIA fixture, Sepang, Malaysia",result.address());assertEquals(2.7,result.latitude());assertEquals(101.7,result.longitude());
    }
    @Test void cachesQueriesCaseInsensitively() {assertEquals(service.suggest("KLIA"),service.suggest("klia"));assertEquals(1,requests.get());}
    @Test void minimumLengthPreventsCalls() {assertThrows(ResponseStatusException.class,()->service.suggest("KL"));assertEquals(0,requests.get());}
    @Test void rateLimitsDifferentQueries() {service.suggest("KLIA");assertEquals(429,assertThrows(ResponseStatusException.class,()->service.suggest("KL Sentral")).getStatusCode().value());assertEquals(1,requests.get());}
    @Test void rejectsInvalidCoordinates() {body=body.replace("101.7,2.7","999,999");assertThrows(ResponseStatusException.class,()->service.suggest("KLIA"));}
    @Test void emptyResultsAreNotInvented() {body="{\"features\":[]}";assertTrue(service.suggest("KLIA").isEmpty());}
    @Test void providerFailuresHaveNoFallback() {status=503;assertThrows(ResponseStatusException.class,()->service.suggest("KLIA"));}
}
