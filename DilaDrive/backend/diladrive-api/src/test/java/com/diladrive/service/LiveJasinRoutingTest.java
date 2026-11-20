package com.diladrive.service;

import com.diladrive.dto.TripQuoteRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

/** Real providers and default launch configuration; no datasource or mock services. */
@EnabledIfEnvironmentVariable(named="RUN_LIVE_JASIN_ROUTE", matches="true")
@SpringBootTest(classes=LiveJasinRoutingTest.Config.class, webEnvironment=SpringBootTest.WebEnvironment.NONE)
class LiveJasinRoutingTest {
    @Configuration
    @org.springframework.boot.test.context.TestComponent
    @Import({RoadRoutingService.class, LocationGatewayService.class, LocationAutocompleteService.class})
    static class Config { @Bean ObjectMapper json() { return new ObjectMapper(); } }
    @Autowired RoadRoutingService routing;
    @Autowired LocationGatewayService locations;
    @Autowired LocationAutocompleteService autocomplete;
    @Autowired ObjectMapper json;

    @Test void actualJasinToKliaProviders() throws Exception {
        var pickups=locations.search("UiTM Jasin");
        var pickup=pickups.stream().filter(p->p.address().contains("UiTM Kampus Jasin")).findFirst().orElseThrow();
        Thread.sleep(1200);
        var destinations=locations.search("KLIA Terminal 1");
        var destination=destinations.stream().filter(p->p.address().contains("Main Building")).findFirst().orElseThrow();
        Thread.sleep(1200);
        var reverse=locations.reverse(pickup.latitude(),pickup.longitude());
        assertFalse(reverse.address().isBlank());
        var suggestions=autocomplete.suggest("UiTM Jasin");
        assertFalse(suggestions.isEmpty());
        var request=new TripQuoteRequest(pickup.address(),destination.address(),pickup.latitude(),pickup.longitude(),
                destination.latitude(),destination.longitude(),LocalDateTime.now().plusDays(1),1,0,0,0,"NORMAL_TRANSFER",null,null);
        var route=routing.route(request);
        assertTrue(route.distanceKm()>0);
        assertTrue(route.durationSeconds()>0);
        assertTrue(route.geometry().size()>2);
        Files.writeString(Path.of("target/jasin-route-provider-response.json"),json.writeValueAsString(route));
        Files.writeString(Path.of("target/jasin-location-provider-response.json"),json.writeValueAsString(
                java.util.Map.of("pickupResults",pickups,"destinationResults",destinations,"reverse",reverse,"suggestions",suggestions)));
        System.out.printf("LIVE PROVIDERS: %.4f km; %d seconds; %d geometry points; pickup %s; destination %s%n",
                route.distanceKm(),route.durationSeconds(),route.geometry().size(),pickup.address(),destination.address());
    }
}
