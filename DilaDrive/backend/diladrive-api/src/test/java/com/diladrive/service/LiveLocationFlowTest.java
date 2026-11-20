package com.diladrive.service;
import com.diladrive.dto.TripQuoteRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in development check, not bulk geocoding or production route configuration. */
@EnabledIfEnvironmentVariable(named="RUN_LIVE_LOCATION_FLOW",matches="true")
class LiveLocationFlowTest {
    @Test void KLIAtoKLSentralUsesRealSuggestionsAndDrivingGeometry() throws Exception {
        var json=new ObjectMapper();
        var suggestions=new LocationAutocompleteService("https://photon.komoot.io","DilaDrive/0.1 (location flow verification)","my",json);
        var pickups=suggestions.suggest("KLIA");assertFalse(pickups.isEmpty());
        Thread.sleep(1200);
        var destinations=suggestions.suggest("KL Sentral");assertFalse(destinations.isEmpty());
        // KLIA can also match the expressway. Select the actual returned terminal,
        // just as a customer selects a matching suggestion, never invent coordinates.
        var pickup=pickups.stream().filter(p->p.address().contains("KLIA T1")).findFirst().orElseThrow();
        var destination=destinations.get(0);
        var reverse=new LocationGatewayService("https://nominatim.openstreetmap.org","DilaDrive/0.1 (location flow verification)","my",json);
        assertFalse(reverse.reverse(pickup.latitude(),pickup.longitude()).address().isBlank());
        var request=new TripQuoteRequest(pickup.address(),destination.address(),pickup.latitude(),pickup.longitude(),
                destination.latitude(),destination.longitude(),LocalDateTime.now(ZoneId.of("Asia/Kuala_Lumpur")).plusDays(1),1,0,0,0,"NORMAL_TRANSFER",null,null);
        var route=new RoadRoutingService("https://router.project-osrm.org",json).route(request);
        assertTrue(route.distanceKm()>0);assertTrue(route.durationSeconds()>0);assertTrue(route.geometry().size()>1);
        System.out.printf("LIVE VERIFIED: %s -> %s; driving %.2f km, %d seconds, %d route points.%n",
                pickup.address(),destination.address(),route.distanceKm(),route.durationSeconds(),route.geometry().size());
    }
}
