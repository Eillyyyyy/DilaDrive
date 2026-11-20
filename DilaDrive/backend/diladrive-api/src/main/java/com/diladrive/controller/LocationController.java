package com.diladrive.controller;

import com.diladrive.service.LocationGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/locations")
public class LocationController {
    private final LocationGatewayService locations;
    private final com.diladrive.service.LocationAutocompleteService autocomplete;
    public LocationController(LocationGatewayService locations) { this.locations = locations; this.autocomplete=null; }
    @org.springframework.beans.factory.annotation.Autowired
    public LocationController(LocationGatewayService locations, com.diladrive.service.LocationAutocompleteService autocomplete) {
        this.locations=locations;this.autocomplete=autocomplete;
    }

    @GetMapping("/search")
    public Map<String,Object> search(@RequestParam String q,
            @RequestParam(defaultValue="false", name="autocomplete") boolean suggestions, HttpServletRequest request) {
        requireCustomer(request);
        if(suggestions && autocomplete==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Restart the API to enable address suggestions.");
        return Map.of("results", suggestions ? autocomplete.suggest(q) : locations.search(q), "attribution", "© OpenStreetMap contributors");
    }

    @GetMapping("/reverse")
    public LocationGatewayService.Location reverse(@RequestParam double latitude,
            @RequestParam double longitude, HttpServletRequest request) {
        requireCustomer(request);
        return locations.reverse(latitude, longitude);
    }

    private void requireCustomer(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute("mobileUserId") instanceof Long)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again to search addresses.");
        }
        if (!"CUSTOMER".equals(session.getAttribute("mobileRole"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer sign-in is required to search addresses.");
        }
    }
}
