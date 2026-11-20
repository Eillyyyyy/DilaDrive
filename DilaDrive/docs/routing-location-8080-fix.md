# Routing and location configuration repair — 18 November 2026

## Causes

The normal `mvn spring-boot:run` process had no explicit active Spring profile.
`application.properties` selects `development`, but only `application-local.properties`
provided a development OSRM URL. Consequently `booking.routing-url` was empty and
`RoadRoutingService` returned 503 before any external routing request. This gap was
introduced when the default development profile was added for CORS.

The running JVM did not have the Windows trust-store arguments present in the VS Code
local launch configuration. Using that exact Java 25 runtime reproduced
`SSLHandshakeException: PKIX path building failed` against both Nominatim and Photon.
The same requests with Windows-ROOT returned HTTP 200. The location services previously
swallowed the exception details and mapped the connection failure to 502. The old
terminal's backend output was not persisted in the available IDE log files, so the
TLS diagnosis uses the actual runtime/configuration and reproduced exception, rather
than claiming an original stack trace was recovered.

The public geocoder also intermittently timed out during live checks. These failures
now produce 504 and server-side diagnostics. An isolated retry using the real services
passed. Public-provider availability remains outside DilaDrive's control.

## Changes and configuration

- The default `development` profile now supplies `https://router.project-osrm.org`,
  as `local` already did. `BOOKING_ROUTING_URL` still overrides it. Explicit production
  profiles still require their own OSRM-compatible URL; they do not inherit the demo.
- A Windows-only Maven profile sets the Spring Boot run JVM's Windows-ROOT trust store.
  Certificate and hostname verification remain enabled. Other operating systems retain
  Java's default trust. Direct IDE/JAR launches must configure their own trust store;
  the existing VS Code local launch and `run-local.ps1 -UseWindowsTrust` already do so.
  Custom deployments can override `spring-boot.run.jvmArguments`.
- Routing, geocoding and autocomplete log provider failures. Geocoding and autocomplete
  use `BOOKING_LOCATION_TIMEOUT_SECONDS` (default 12, below Flutter's 15-second deadline)
  and distinguish provider timeouts (504), bad upstream responses (502), rate limits
  (429), no reverse address (404), and invalid input (400).
- Flutter preserves a reverse-geocoding 404 message instead of incorrectly claiming
  that the API endpoint is missing. Only `ENDPOINT_NOT_FOUND` triggers that diagnostic.
- No routing provider was replaced. OSRM, public Nominatim and Photon require no API
  key for these existing development endpoints. No keys were added to client code.
- No change to database schema/records, strict vehicle-availability rules, assignments,
  admin or driver functionality. No clean/package/application build was run; Maven
  compiled changed backend sources as required by tests, and the existing application
  classpath was reused to restart the normal API on 8080.

The real OSRM Route API supplies road metres, duration seconds and GeoJSON coordinates:
https://project-osrm.org/docs/v26.5.0/http . Windows-ROOT is a native trusted root
keystore, not a certificate-validation bypass:
https://docs.oracle.com/en/java/javase/27/security/oracle-providers.html .

## Verification and limits

Files changed for this repair (paths relative to `diladrive/`):

- `backend/diladrive-api/pom.xml`
- `backend/diladrive-api/src/main/resources/application.properties`
- `backend/diladrive-api/src/main/resources/application-development.properties`
- `backend/diladrive-api/src/main/java/com/diladrive/service/RoadRoutingService.java`
- `backend/diladrive-api/src/main/java/com/diladrive/service/LocationGatewayService.java`
- `backend/diladrive-api/src/main/java/com/diladrive/service/LocationAutocompleteService.java`
- `backend/diladrive-api/src/test/java/com/diladrive/service/RoutingProfileTest.java` (new)
- `backend/diladrive-api/src/test/java/com/diladrive/service/LiveJasinRoutingTest.java` (new)
- `mobile-app/lib/services/booking_location_service.dart`
- `mobile-app/test/booking_location_selection_test.dart`
- `docs/routing-location-8080-fix.md` (this report)

The normal API was restarted on 8080 with Windows trust enabled. Its log confirms
`development` as the default profile and Tomcat listening on 8080:
`backend/diladrive-api/target/routing-api-final.log`.

Actual HTTP checks against that process:

- OPTIONS `/api/bookings/route`: 200, credentialed localhost CORS headers present.
- POST `/api/auth/mobile/login` with deliberately invalid credentials: 401.
- Unauthenticated `/api/locations/search`: 401, preserving authentication.
- GET `/api/bookings/route`: 405 with `Allow: POST`; the existing Flutter client uses POST.

Actual live default-profile services, without a datasource or mocked providers:

- Search returned UiTM Kampus Jasin at **2.2254670, 102.4556074** and KLIA Terminal 1
  Main Building at **2.7527291, 101.7055234**. The campus was selected from returned
  results, rather than the similarly named Jalan UITM road result.
- Address search, reverse lookup and Photon suggestions all passed.
- OSRM route: **139.6706 km**, **6829 seconds** (about **114 minutes**), **1469 geometry
  points**, in longitude/latitude order. No straight-line fallback or route fixtures.
- Responses saved under `backend/diladrive-api/target/`:
  `jasin-route-provider-response.json`, `jasin-location-provider-response.json`.
- Live test: `LiveJasinRoutingTest`, enabled with `RUN_LIVE_JASIN_ROUTE=true`, passed
  on isolated retry. Earlier live attempts timed out; they are not counted as successes.
- Final backend regression: **204 discovered, 199 passed, 5 opt-in tests skipped**;
  zero failures/errors. Includes default/production profile regression and existing
  login/session, routing, location and strict-availability business-rule tests.
- Flutter analyze: **no issues**. Flutter location tests could not start because
  `pdfium_dart` needs a Windows C++ compiler unavailable in this environment.

Authenticated route/search/reverse/availability responses from port 8080 still require
an existing test customer credential or current session, requested from the user.
No success is claimed for those protected endpoints. Browser/Android end-to-end UI
verification, including route rendering and Continue, also remains unverified; no
browser-control tool is available in this session. Existing code validates positive
distance/duration and usable geometry before enabling Continue, displays the polyline
and minutes, and supports route retry. Search failures leave committed selections intact.

Do not run the older disposable-schema live test to fill these gaps: it creates and
drops database fixtures. The new service-only live test performs no database operations.
