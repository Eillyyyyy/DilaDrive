# Pickup and drop-off selection: implementation and run guide

## Run the updated app

The API already running on port **8080 returned 404** for `/api/locations/search` during
inspection. It did not expose the implemented location gateway. That is why changing
Flutter alone could not make searches work. Its process could not be verified as this
checkout, so it was left running.

The tested jar from **this workspace** is now running separately on **8081**, with schema
validation, the existing PostgreSQL connection and a real public OSRM development router.
An anonymous request to its location endpoint returns **401**, confirming the endpoint is
mapped and still requires customer authentication. No company rows or tables were changed.

In VS Code, select **DilaDrive Chrome - workspace API 8081** from the repository-root
`.vscode/launch.json`, fully restart Flutter and sign in again. It sets the correct API
and location gateway URLs. The Chrome profile uses 127.0.0.1 for both the app host and API
so its session cookie does not replace the old localhost server's browser cookie.

Or run this from `diladrive/mobile-app`:

```text
flutter run -d chrome --web-hostname=127.0.0.1 --dart-define=API_BASE_URL=http://127.0.0.1:8081 --dart-define=BOOKING_LOCATION_API_URL=http://127.0.0.1:8081/api/locations
```

The **DilaDrive Android emulator - workspace API 8081** profile uses 10.0.2.2:8081. For a
physical Android device, replace that emulator address with the PC's reachable LAN IP
or a deployed backend URL. No Android device was connected during this work.

Workspace server files:

- Jar: `backend/diladrive-api/target/diladrive-api-0.1.0.jar`
- PID: `backend/diladrive-api/target/location-dev-api.pid`
- Logs: `target/location-dev-api.stdout.log` and `target/location-dev-api.stderr.log`

If restarting it yourself, run the newly built jar from its backend directory, for example:

```text
java -jar target/diladrive-api-0.1.0.jar --server.port=8081 --booking.routing-url=https://router.project-osrm.org
```

This router is a development setting, not company production infrastructure. The local
process also uses the Windows certificate store because of this machine's JVM trust
configuration; configure a trusted Java CA store if a different JVM reports PKIX errors.
Certificate verification was not disabled.

## Root causes fixed

- The running backend lacked the gateway endpoint.
- The 5-character minimum rejected `KLIA`; all search layers now accept 3–500 characters.
- Public Nominatim cannot be used for autocomplete. Explicit Search/reverse still use
  Nominatim, while suggestions use Photon, which supports search-as-you-type.
- Map taps previously opened a modal and waited for reverse geocoding before storing
  a pin. Failed lookups left no marker. Pin coordinates are now stored immediately.
- The location draft was recreated whenever the profile builder rebuilt. CreateBookingPage
  now retains one draft for the lifetime of the flow.

## Screen behavior

The existing page now delegates selection to `BookingLocationView`; it still pushes the
existing `BookingDetailsPage` with the same BookingDraft. No booking module was recreated.
The view is separated from navigation so the actual selection UI can be widget-tested
without importing unrelated PDF/native modules.

At the top are pickup/drop-off search tiles, coordinates, current location and explicit
**Pin pickup / Pin drop-off** modes. Tapping a search tile opens the existing full-screen
search page, keeping suggestions above the map and keyboard handling intact.

Suggestions wait for a **1.2-second pause** and at least **3 characters**. Editing cancels
the debounce timer and invalidates prior requests; late results cannot replace a newer
query. Search remains available as an explicit fallback. Only validated formatted
addresses with finite, in-range coordinates can be selected.

Map taps immediately update the selected endpoint, display its marker and start reverse
lookup. Pickup is green; drop-off is red. The other endpoint is unchanged. Each endpoint
has its own request version so delayed reverse results cannot move a newer pin. Reverse
failure retains the real coordinates and offers Retry or explicit address confirmation
for that pin. Unresolved text without a pin never becomes a valid booking location.

The map camera moves to a single selected point and fits both endpoints/full route when
available. Camera updates use MapController rather than recreating the whole map on each
selection. The existing swap control is retained and recalculates the route in the new
direction. Map gestures are not covered by a full-size overlay.

When both addresses and pins are valid, a short debounce requests the existing Spring
Boot road-routing API automatically. The UI displays the returned driving distance,
duration and geometry. BookingDraft stores the route and exposes `routeDistanceKm` and
`routeDurationSeconds`. A changed endpoint clears stale availability/fare/route data.
Old routing responses are ignored. Failed or malformed routes leave Continue disabled
and expose Retry; no straight-line or fake distance/duration is substituted.

## Providers and configuration

No API keys are required for the current low-volume public development services.

| Setting | Purpose |
| --- | --- |
| Flutter `API_BASE_URL` | All existing authenticated API calls. Launch profiles point to the updated API on 8081. |
| Flutter `BOOKING_LOCATION_API_URL` | Existing gateway override; otherwise defaults to `${API_BASE_URL}/api/locations`. |
| Flutter `BOOKING_LOCATION_AUTOCOMPLETE` | Opt-in for an external custom gateway that implements `autocomplete=true`; first-party gateways enable it automatically. Do not enable this for an adapter that forwards typing to public Nominatim. |
| Backend `BOOKING_AUTOCOMPLETE_URL` | Photon-compatible base URL; default `https://photon.komoot.io`. |
| Backend `BOOKING_GEOCODING_URL` | Nominatim-compatible explicit-search/reverse URL; default `https://nominatim.openstreetmap.org`. |
| Backend `BOOKING_GEOCODING_COUNTRIES` | Default `my`; empty for unrestricted search. |
| Backend `BOOKING_GEOCODING_USER_AGENT` | Stable DilaDrive identity; configure real operator contact information for deployment. |
| Backend `BOOKING_ROUTING_URL` | OSRM-compatible driving router. Source configuration has no fake fallback. The running 8081 development process explicitly supplies the public OSRM URL. |
| Flutter `MAP_TILE_URL` | Existing tile-provider override; OSM tiles remain the default. |

`GET /api/locations/search?q=...&autocomplete=true` uses Photon. Without the flag it
continues using Nominatim. Both preserve the existing JSON result contract and customer
session checks. The reverse endpoint and booking routing endpoints are unchanged.

Each geocoding adapter has a shared server-side limiter (at least 1.1 seconds between
uncached upstream requests), bounded 24-hour caching and 429 backoff. Public Nominatim
is never called by the autocomplete timer. Use a single development gateway; production
or multi-instance deployments need suitable hosted/self-hosted services and shared limits.

The OSM tile warning is not suppressed. Existing HTTPS tiles, correct app identification,
linked attribution, browser referrer policy, native persistent HTTP-aware caching and
viewport-only tile loading remain configured. Production capacity is not guaranteed by
public demo services.

Policies/API references: [Photon](https://github.com/komoot/photon),
[Photon search API](https://github.com/komoot/photon/blob/master/docs/api-v1.md),
[Nominatim policy](https://operations.osmfoundation.org/policies/nominatim/),
[OSM tile policy](https://operations.osmfoundation.org/policies/tiles/).

## Verification

- **23 Flutter tests passed** using the actual location view, search page, providers and
  map widget in the lightweight dependency harness. They exercise KLIA/KL Sentral
  suggestions, debounce, both selections, actual FlutterMap taps, immediate pins,
  independent endpoint changes, both visible camera bounds, reverse failure/retry/manual
  confirmation, route geometry/metrics, Continue callback with the same draft, GPS,
  stale lookup/autocomplete/route rejection, unresolved-text rejection and swapping.
  Test-only tile images prevent automated traffic to public OSM tiles.
- **188 backend regression tests passed**; two optional live test classes were skipped
  in the default suite. New tests cover Photon parsing, latitude/longitude order,
  minimum length, cache/rate limits and autocomplete dispatch without Nominatim calls.
- An opt-in **live provider check passed**: real KLIA suggestions include KLIA T1/T2 as
  well as expressway matches. Selecting the returned **KLIA T1** result and KL Sentral
  produced a real OSRM driving route of **58.35 km**, **3274 seconds**, **820 geometry
  points** during verification. These values are observations, not constants in the app.
  An earlier run selected KLIA Expressway; no airport claim is based on that result.
- Ran dart format and full flutter analyze: no introduced diagnostics. The existing
  unused `bookings.dart` import in customer `profile.dart:11` remains outside this task.
- Web build passed with API 8081. Android debug APK build passed with emulator API 8081:
  `mobile-app/build/app/outputs/flutter-apk/app-debug.apk`.
- Full-project `flutter test` was attempted but stops in the existing pdfium_dart native
  hook because this Windows machine has no configured C compiler. The dependency harness
  avoids that unrelated module without modifying app source for tests.
- Headless Chrome tests were attempted. This installed Windows Flutter test server
  returned 404 for CanvasKit assets. A test-only bridge supplied the identical SDK
  renderer files, but the suite still did not start, so **no successful full-screen
  Chrome runtime test is claimed**. The stalled test browser/helpers were closed.
- `flutter devices` found Windows, Chrome and Edge; no physical Android/emulator was
  connected. Actual device GPS/permission checks and a signed-in browser session remain
  manual. Use the new launch profiles, choose the matching suggestions, pin both modes,
  retry failed lookups/routes and continue to Trip Details.

## Files changed

Created:

- `.vscode/launch.json` at the repository root.
- `mobile-app/lib/screens/customer/booking/booking_location_view.dart`.
- `mobile-app/test/booking_location_flow_test.dart`.
- Backend `service/LocationAutocompleteService.java`.
- Backend tests `LocationAutocompleteServiceTest.java`, `LiveLocationFlowTest.java`.
- This guide.

Modified:

- Flutter `booking_location_page.dart`, `booking_location_search_page.dart`,
  `booking_location_service.dart`, `booking_route_map.dart`, `booking_draft.dart`,
  `screens/customer/create_booking.dart`.
- Flutter `.gitignore` excludes the generated, dependency-isolated test harness.
- Existing Flutter tests `booking_flow_test.dart`, `booking_location_material_test.dart`,
  `booking_location_selection_test.dart`.
- Backend `LocationController.java`, `LocationGatewayService.java`,
  `application.properties`, `LocationControllerTest.java`, `LocationGatewayServiceTest.java`.
- Gateway documentation, updated with a link to this current screen/run guide.

Authentication logic, Admin/Driver pages, booking assignment APIs, pricing records and
PostgreSQL data were preserved. No database migration was introduced for this change.
