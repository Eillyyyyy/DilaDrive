# Road routing HTTP 503 fix

## Root cause

The backend on port 8080 was started without `BOOKING_ROUTING_URL` or a routing profile. The base property is `booking.routing-url=${BOOKING_ROUTING_URL:}`. `RoadRoutingService` deliberately returns 503 with “Road routing is not configured” when that value is blank, before making any external request. The previous separately launched 8081 instance had a transient routing argument; that did not configure 8080. Flutter, coordinates, authentication to a provider, and provider downtime did not cause this particular message.

## Changes

- `backend/diladrive-api/src/main/resources/application-local.properties`: explicit local development OSRM URL and loopback-only development CORS patterns. Base/production routing remains explicitly configured, with no public demo fallback.
- `backend/diladrive-api/run-local.ps1`: repeatable local-profile launcher, port 8080 by default, optional Windows certificate-store support.
- `backend/diladrive-api/src/main/java/com/diladrive/service/RoadRoutingService.java`: retain actual OSRM driving routing; validate finite coordinates and geometry; identify upstream requests; limit demo requests to one per 1.1 seconds per backend instance and cache up to 128 endpoint pairs; distinguish missing configuration (503), timeout (504), no road connection (422), and provider/authentication/malformed response failures (502).
- `backend/diladrive-api/src/main/java/com/diladrive/config/CorsConfig.java`: honor the existing allowed-origins configuration. Only the local profile defaults to loopback wildcard ports; the base configuration defaults to the existing exact localhost:5173 origin.
- `backend/diladrive-api/src/test/java/com/diladrive/service/RoadRoutingServiceTest.java`: success, missing configuration, invalid coordinates, no route, provider unavailable/authentication failure, malformed geometry and timeout checks.
- `backend/diladrive-api/src/test/java/com/diladrive/LiveRoadRouteTest.java`: opt-in real HTTP login and routing, geocoded real destinations, isolated disposable database schema, invalid coordinate and CORS checks. Never creates fixtures in the normal application schema.
- `mobile-app/lib/widgets/booking_route_map.dart`: include OSRM routing attribution alongside existing linked OpenStreetMap attribution.
- Root `.vscode/launch.json`: add API 8080 local-profile and matching Chrome launch configurations. Existing 8081 configurations remain available.

POST `/api/bookings/route` and existing DTOs remain unchanged: response `distanceKm`, `durationSeconds`, `geometry` in `[longitude, latitude]` order. Existing Flutter code already draws the polyline, saves metrics to BookingDraft, enables Continue only for valid route data, clears route data on endpoint edits, ignores outdated route responses and offers Retry on failure. Search, GPS, map selection and the theme remain intact.

## Run locally

From the repository root in PowerShell, with JDK and Maven installed:

```powershell
cd diladrive/backend/diladrive-api
./run-local.ps1 -UseWindowsTrust
```

`-UseWindowsTrust` uses Windows trusted certificate authorities; it does not disable HTTPS verification. It is useful for this machine's JDK certificate setup and is optional where normal Java HTTPS trust already works. Set JAVA_HOME to your installed JDK if needed. Stop an existing backend on the same port before starting another.

Equivalent standard Maven command:

```powershell
mvn spring-boot:run '-Dspring-boot.run.profiles=local'
```

Or select **DilaDrive API 8080 - local road routing**, then **DilaDrive Chrome - API 8080** in VS Code. Sign in again after a backend restart. Keep the Chrome app and API on the same hostname (both 127.0.0.1 or both localhost) for the existing strict session cookie policy.

### Configuration

- Development: `SPRING_PROFILES_ACTIVE=local` supplies `https://router.project-osrm.org`; no routing API key needed.
- `BOOKING_ROUTING_URL`: OSRM-compatible backend base URL, for example `https://router.project-osrm.org` for light development, or your own OSRM server for deployment. Do not append `/route/v1/driving` to this value.
- Production: activate your deployment profile and explicitly set `BOOKING_ROUTING_URL` and `CORS_ALLOWED_ORIGINS` to your deployed service and exact frontend origins. No routing secrets belong in Flutter.
- Existing `DATABASE_*` settings remain unchanged.
- Flutter: `API_BASE_URL=http://127.0.0.1:8080` for Chrome, `http://10.0.2.2:8080` for Android emulator; physical devices need the backend computer's LAN address. Existing location gateway defaults to that API's `/api/locations`; no new location key is required.

OSRM's public demo is a development service with no availability guarantee. Its public usage policy requires moderate use and attribution; do not use multiple backend replicas to multiply the request allowance. For production, configure a managed or self-hosted OSRM instance. References: https://github.com/Project-OSRM/osrm-backend/wiki/Api-usage-policy and https://routing.openstreetmap.de/about.html .

## Verified results — 17 November 2026

Actual authenticated POST to **http://127.0.0.1:8080/api/bookings/route** returned **HTTP 200**:

- Pickup: UiTM Shah Alam campus, latitude **3.0686895**, longitude **101.4995983**.
- Destination: KLIA Terminal 1 Main Building, latitude **2.7527291**, longitude **101.7055234**.
- OSRM driving distance: **53.3996 km**.
- Duration: **3056 seconds**, approximately **51 minutes**.
- Geometry: **700 valid coordinates**. Not a straight-line estimate.
- Real request and response saved to `backend/diladrive-api/target/live-road-route-request.json` and `target/live-road-route-response.json`.
- Invalid API coordinates: **400**. Allowed local CORS preflight: **200** with reflected allowed origin. Untrusted external origin: **403**.

The live test used actual customer login, the real controller/service and the external provider, on port 8080, with fixtures isolated in a temporary schema that was removed after the test. The normal development backend was then restored on 8080 with the local profile; the previously available 8081 development service was restored too.

Backend regression suite: **196 discovered, 193 passed, 3 opt-in live tests skipped**, no failures. Separately enabled live API test plus routing unit tests: **9 passed**. Packaging succeeded after releasing the running development JAR's Windows file lock.

Flutter: `dart format` run on the modified Dart file. `flutter analyze` completed with one existing unrelated unused import warning in `lib/screens/customer/profile.dart:11`. The normal `flutter test --no-pub` is blocked before tests execute by `pdfium_dart` requiring a Windows C compiler. The existing isolated location harness (using the actual app source via its lib junction) passed **23 widget tests**, including markers, draft metrics, route polyline, Continue, retries, endpoint changes and stale response rejection.

Chrome and Android visual/device verification still needs manual testing: select the two destinations, check the road polyline and summary, Continue to Trip Details, then change either endpoint and confirm the route updates. Automated widget checks passed; they do not constitute a real browser/device interaction test. The flutter_map public OSM debug reminder remains visible and was not suppressed.

Repeat the live API test (stop the normal 8080 development service first):

```powershell
$env:RUN_LIVE_ROAD_ROUTE='true'
$env:LIVE_ROAD_ROUTE_PORT='8080'
mvn '-Dtest=LiveRoadRouteTest,RoadRoutingServiceTest' '-DargLine=-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE' test
Remove-Item Env:RUN_LIVE_ROAD_ROUTE, Env:LIVE_ROAD_ROUTE_PORT
```

The Byte Buddy option is needed on this machine's Java 25 test runtime. Tests require access to PostgreSQL to create/drop the uniquely named test schema and network access to geocoding and OSRM.
