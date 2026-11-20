package com.diladrive.service;

import com.diladrive.dto.TripQuoteRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;

/** OSRM-compatible driving router. No straight-line fallback. */
@Service
public class RoadRoutingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RoadRoutingService.class);
    private final String baseUrl;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final Duration timeout;
    private long lastDemoRequest;
    private final java.util.Map<String, Route> demoCache = new java.util.LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Route> entry) { return size() > 128; }
    };
    @org.springframework.beans.factory.annotation.Autowired
    public RoadRoutingService(@Value("${booking.routing-url:}") String baseUrl, ObjectMapper mapper) {
        this(baseUrl, mapper, Duration.ofSeconds(15));
    }
    RoadRoutingService(String baseUrl, ObjectMapper mapper, Duration timeout) {
        this.baseUrl = baseUrl.trim(); this.mapper = mapper; this.timeout = timeout;
    }
    public record Route(double distanceKm, long durationSeconds, List<List<Double>> geometry) { }
    public synchronized Route route(TripQuoteRequest r) {
        if (!coordinate(r.pickupLatitude(), 90) || !coordinate(r.destinationLatitude(), 90)
                || !coordinate(r.pickupLongitude(), 180) || !coordinate(r.destinationLongitude(), 180))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pickup and destination coordinates are invalid.");
        if (baseUrl.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Road routing is not configured. Please contact DilaDrive.");
        try {
            String path = String.format(java.util.Locale.ROOT, "/route/v1/driving/%s,%s;%s,%s?overview=full&geometries=geojson",
                    r.pickupLongitude(), r.pickupLatitude(), r.destinationLongitude(), r.destinationLatitude());
            boolean demo = "router.project-osrm.org".equalsIgnoreCase(URI.create(baseUrl).getHost());
            if (demo) {
                if (demoCache.containsKey(path)) return demoCache.get(path);
                long delay = 1100 - (System.nanoTime() - lastDemoRequest) / 1_000_000;
                if (delay > 0) Thread.sleep(delay);
                lastDemoRequest = System.nanoTime();
            }
            var response = client.send(HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/$", "") + path))
                    .header("User-Agent", "DilaDrive/0.1 (customer driving route preview)")
                    .timeout(timeout).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Routing provider {} returned HTTP {}", URI.create(baseUrl).getHost(), response.statusCode());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "The road routing provider is unavailable (HTTP " + response.statusCode() + "). Retry Route shortly.");
            }
            var root = mapper.readTree(response.body());
            if ("NoRoute".equals(root.path("code").asText()) || "NoSegment".equals(root.path("code").asText()))
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No driving route connects these locations. Move the pins onto accessible roads and retry.");
            if (response.statusCode() != 200 || !"Ok".equals(root.path("code").asText()) || root.path("routes").isEmpty())
                throw new IllegalStateException("No driving route");
            var route = root.path("routes").get(0);
            double metres = route.path("distance").asDouble(-1);
            double seconds = route.path("duration").asDouble(-1);
            if (!Double.isFinite(metres) || !Double.isFinite(seconds) || metres <= 0 || seconds <= 0)
                throw new IllegalStateException("Invalid route");
            List<List<Double>> geometry = mapper.convertValue(route.path("geometry").path("coordinates"),
                    new com.fasterxml.jackson.core.type.TypeReference<List<List<Double>>>() {});
            if (geometry == null || geometry.size() < 2 || geometry.stream().anyMatch(point ->
                    point == null || point.size() < 2 || !coordinate(point.get(0), 180) || !coordinate(point.get(1), 90)))
                throw new IllegalStateException("Invalid route geometry");
            Route result = new Route(metres / 1000, (long)Math.ceil(seconds), geometry);
            if (demo) demoCache.put(path, result);
            return result;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            log.warn("Routing provider {} timed out: {}", URI.create(baseUrl).getHost(), e.toString());
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "The road routing provider timed out. Retry Route shortly.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Road routing was interrupted.");
        } catch (Exception e) {
            log.warn("Routing provider request failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "A road route could not be calculated. Check the locations and retry.");
        }
    }
    private static boolean coordinate(Double value, int limit) {
        return value != null && Double.isFinite(value) && Math.abs(value) <= limit;
    }
}
