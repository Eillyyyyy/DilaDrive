package com.diladrive.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Photon explicitly supports search-as-you-type. Never autocomplete against public Nominatim. */
@Service
public class LocationAutocompleteService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LocationAutocompleteService.class);
    private record Cached(List<LocationGatewayService.Location> results, long expires) {}
    private final String url, userAgent, countries;
    private final ObjectMapper json;
    private final Duration timeout;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private long nextRequest;
    private final Map<String,Cached> cache = new LinkedHashMap<>(64,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Cached> entry) { return size()>512; }
    };
    public LocationAutocompleteService(String url, String userAgent, String countries, ObjectMapper json) {
        this(url, userAgent, countries, json, 12);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LocationAutocompleteService(
            @Value("${booking.autocomplete-url:https://photon.komoot.io}") String url,
            @Value("${booking.geocoding-user-agent:DilaDrive/0.1 (transport booking)}") String userAgent,
            @Value("${booking.geocoding-countries:my}") String countries, ObjectMapper json,
            @Value("${booking.location-timeout-seconds:12}") int timeoutSeconds) {
        this.url=url.replaceAll("/+$","");this.userAgent=userAgent;this.countries=countries;this.json=json;
        this.timeout=Duration.ofSeconds(timeoutSeconds);
    }
    public synchronized List<LocationGatewayService.Location> suggest(String query) {
        String q=query==null?"":query.trim().replaceAll("\\s+"," ");
        if(q.length()<3 || q.length()>500) throw fail(HttpStatus.BAD_REQUEST,"Enter at least 3 characters for address suggestions.");
        var saved=cache.get(q.toLowerCase(Locale.ROOT));
        long now=System.nanoTime();
        if(saved!=null && saved.expires()>now) return saved.results();
        if(now<nextRequest) throw fail(HttpStatus.TOO_MANY_REQUESTS,"Address suggestions are busy. Wait a moment and retry.");
        if(url.isBlank()) throw fail(HttpStatus.SERVICE_UNAVAILABLE,"Address autocomplete is not configured. Use Search instead.");
        nextRequest=now+Duration.ofMillis(1100).toNanos();
        try {
            var builder=UriComponentsBuilder.fromUriString(url+"/api/").queryParam("q","{q}").queryParam("limit",5).queryParam("lang","en");
            for(String country:countries.split(",")) if(!country.isBlank()) builder.queryParam("countrycode",country.trim().toUpperCase(Locale.ROOT));
            var uri=builder.encode().buildAndExpand(Map.of("q",q)).toUri();
            var response=client.send(HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("User-Agent",userAgent).header("Accept","application/json").GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==429) {nextRequest=System.nanoTime()+Duration.ofMinutes(1).toNanos();throw fail(HttpStatus.TOO_MANY_REQUESTS,"Address suggestions are busy. Retry in a minute.");}
            if(response.statusCode()!=200) {
                log.warn("Autocomplete provider returned HTTP {}",response.statusCode());
                throw fail(HttpStatus.BAD_GATEWAY,"Address suggestions are unavailable. Retry or use Search.");
            }
            var features=json.readTree(response.body()).path("features");
            if(!features.isArray()) throw new IllegalArgumentException();
            var results=new ArrayList<LocationGatewayService.Location>();
            for(var feature:features) {
                var coordinates=feature.path("geometry").path("coordinates");var properties=feature.path("properties");
                if(coordinates.size()<2 || !coordinates.get(0).isNumber() || !coordinates.get(1).isNumber()) throw new IllegalArgumentException();
                double lon=coordinates.get(0).asDouble(),lat=coordinates.get(1).asDouble();
                if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180) throw new IllegalArgumentException();
                var parts=new LinkedHashSet<String>();
                for(String field:List.of("name","street","housenumber","district","city","postcode","state","country")) {
                    String value=properties.path(field).asText("").trim();if(!value.isBlank()) parts.add(value);
                }
                String address=String.join(", ",parts);
                if(address.length()<3 || address.length()>500) throw new IllegalArgumentException();
                results.add(new LocationGatewayService.Location(address,lat,lon));if(results.size()==5)break;
            }
            var immutable=List.copyOf(results);
            cache.put(q.toLowerCase(Locale.ROOT),new Cached(immutable,System.nanoTime()+Duration.ofHours(24).toNanos()));
            return immutable;
        } catch(HttpTimeoutException e) {
            log.warn("Autocomplete provider timed out: {}",e.toString());
            throw fail(HttpStatus.GATEWAY_TIMEOUT,"Address suggestions timed out. Retry or use Search.");
        } catch(InterruptedException e) {Thread.currentThread().interrupt();throw fail(HttpStatus.SERVICE_UNAVAILABLE,"Address suggestions were interrupted. Retry Search.");
        } catch(ResponseStatusException e) {throw e;
        } catch(Exception e) {
            log.warn("Autocomplete provider request failed",e);
            throw fail(HttpStatus.BAD_GATEWAY,"Address suggestions could not be loaded. Retry or use Search.");
        }
    }
    private static ResponseStatusException fail(HttpStatus status,String message) {return new ResponseStatusException(status,message);}
}
