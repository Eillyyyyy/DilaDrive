package com.diladrive.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: exactly one public landmark search and one reverse lookup, not bulk geocoding. */
@EnabledIfEnvironmentVariable(named="RUN_LIVE_GEOCODING_TEST",matches="true")
class LiveLocationGatewayTest {
    @Test void liveSearchAndReverse() throws Exception {
        var service=new LocationGatewayService("https://nominatim.openstreetmap.org",
                "DilaDrive/0.1 (transport booking development verification)","my",new ObjectMapper());
        var results=service.search("Melaka Sentral, Malaysia");
        assertFalse(results.isEmpty());
        var location=results.get(0);
        Thread.sleep(1100);
        var reverse=service.reverse(location.latitude(),location.longitude());
        assertFalse(reverse.address().isBlank());
        System.out.println("Live geocoding verified: formatted search and reverse addresses; valid coordinates.");
    }
}
