package com.diladrive.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** User-triggered Nominatim searches only: no autocomplete, bulk or periodic requests. */
@Service
public class LocationGatewayService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LocationGatewayService.class);
    public record Location(String address, double latitude, double longitude) {}
    private record Cached(JsonNode body, long expiresAt) {}
    private final String baseUrl;
    private final String userAgent;
    private final String countries;
    private final ObjectMapper json;
    private final HttpClient client;
    private final Duration timeout;
    private long lastRequestNanos;
    private final Map<String,Cached> cache = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Cached> entry) {
            return size() > 512;
        }
    };

    public LocationGatewayService(String baseUrl, String userAgent, String countries, ObjectMapper json) {
        this(baseUrl, userAgent, countries, json, 12);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LocationGatewayService(
            @Value("${booking.geocoding-url:https://nominatim.openstreetmap.org}") String baseUrl,
            @Value("${booking.geocoding-user-agent:DilaDrive/0.1 (transport booking)}") String userAgent,
            @Value("${booking.geocoding-countries:my}") String countries,
            ObjectMapper json,
            @Value("${booking.location-timeout-seconds:12}") int timeoutSeconds) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.userAgent = userAgent;
        this.countries = countries;
        this.json = json;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public List<Location> search(String query) {
        String q = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        if (q.length() < 3 || q.length() > 500) {
            throw failure(HttpStatus.BAD_REQUEST, "Enter a specific place or address (3–500 characters).");
        }
        var params = new LinkedHashMap<String,String>();
        params.put("q", q);
        params.put("format", "jsonv2");
        params.put("limit", "5");
        params.put("addressdetails", "1");
        params.put("accept-language", "en");
        if (!countries.isBlank()) params.put("countrycodes", countries);
        JsonNode response = fetch("/search", params);
        if (!response.isArray()) throw invalidResponse();
        List<Location> results = new ArrayList<>();
        for (JsonNode item : response) {
            results.add(location(item));
            if (results.size() == 5) break;
        }
        return List.copyOf(results);
    }

    public Location reverse(double latitude, double longitude) {
        if (!validCoordinates(latitude, longitude)) {
            throw failure(HttpStatus.BAD_REQUEST, "Choose valid latitude and longitude coordinates.");
        }
        JsonNode response = fetch("/reverse", Map.of(
                "lat", Double.toString(latitude), "lon", Double.toString(longitude),
                "format", "jsonv2", "addressdetails", "1", "accept-language", "en"));
        if (response.has("error")) {
            throw failure(HttpStatus.NOT_FOUND, "No address was found at this point. Choose a nearby place.");
        }
        return location(response);
    }

    private Location location(JsonNode item) {
        String address = item.path("display_name").asText("").trim();
        double latitude, longitude;
        try {
            latitude = Double.parseDouble(item.path("lat").asText());
            longitude = Double.parseDouble(item.path("lon").asText());
        } catch (NumberFormatException e) { throw invalidResponse(); }
        if (address.isEmpty() || address.length() > 500 || !validCoordinates(latitude, longitude)) {
            throw invalidResponse();
        }
        return new Location(address, latitude, longitude);
    }

    private static boolean validCoordinates(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    /** One limiter across search and reverse, shared by every user of this backend process. */
    private synchronized JsonNode fetch(String path, Map<String,String> params) {
        if (baseUrl.isBlank() || userAgent.isBlank()) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "The address provider is not configured.");
        }
        var builder = UriComponentsBuilder.fromUriString(baseUrl + path);
        params.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> builder.queryParam(entry.getKey(), "{" + entry.getKey() + "}"));
        URI uri = builder.encode().buildAndExpand(params).toUri();
        Cached saved = cache.get(uri.toString());
        long now = System.nanoTime();
        if (saved != null && saved.expiresAt() > now) return saved.body();
        // Never queue/burst requests at the donated service. Cache hits need no rate slot.
        if (lastRequestNanos != 0 && now - lastRequestNanos < Duration.ofMillis(1100).toNanos()) {
            throw failure(HttpStatus.TOO_MANY_REQUESTS, "Address search is busy. Wait a moment and try again.");
        }
        lastRequestNanos = now;
        try {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                // Back off for a minute if the upstream service requests throttling.
                lastRequestNanos = System.nanoTime() + Duration.ofMinutes(1).toNanos();
                throw failure(HttpStatus.TOO_MANY_REQUESTS, "The address provider is busy. Please retry in a minute.");
            }
            if (response.statusCode() == 404 && path.equals("/reverse")) {
                throw failure(HttpStatus.NOT_FOUND, "No address was found at this point. Choose a nearby place.");
            }
            if (response.statusCode() != 200) {
                log.warn("Geocoding provider {} returned HTTP {} for {}", uri.getHost(), response.statusCode(), path);
                throw failure(HttpStatus.BAD_GATEWAY, "The address provider is unavailable. Please retry shortly.");
            }
            JsonNode body = json.readTree(response.body());
            if (body == null || !(body.isArray() || body.isObject())) throw invalidResponse();
            // Check every coordinate before allowing the response into the cache.
            if (body.isArray()) for (JsonNode item : body) location(item);
            else if (!body.has("error")) location(body);
            cache.put(uri.toString(), new Cached(body,
                    System.nanoTime() + Duration.ofHours(24).toNanos()));
            return body;
        } catch (HttpTimeoutException e) {
            log.warn("Geocoding provider {} timed out for {}: {}", uri.getHost(), path, e.toString());
            throw failure(HttpStatus.GATEWAY_TIMEOUT, "The address provider timed out. Please retry shortly.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "Address lookup was interrupted. Please retry.");
        } catch (ResponseStatusException e) { throw e;
        } catch (Exception e) {
            log.warn("Geocoding provider {} request failed for {}", uri.getHost(), path, e);
            throw failure(HttpStatus.BAD_GATEWAY, "Could not reach the address provider. Please retry shortly.");
        }
    }

    private static ResponseStatusException invalidResponse() {
        return failure(HttpStatus.BAD_GATEWAY, "The address provider returned an invalid location. Please retry.");
    }
    private static ResponseStatusException failure(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
