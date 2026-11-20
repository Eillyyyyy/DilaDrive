# Chrome and Android booking locations

The current screen adds Photon autocomplete, immediate map pins and automatic routing.
See [the current location flow and port-8081 launch guide](location-selection-complete.md)
for those changes and the running development API. Nominatim below remains responsible
only for explicit Search and reverse lookup, not autocomplete.

## What changed

There was no backend geocoding gateway. With no `BOOKING_LOCATION_API_URL`, the old
provider factory selected native geocoding, whose address methods cannot run in Chrome.
The factory now selects `HttpBookingLocationProvider` on both platforms and defaults to
`${AppConfig.apiBaseUrl}/api/locations`. An explicit `BOOKING_LOCATION_API_URL` still
overrides it. GPS and its permission/disabled-service handling remain on the device.

The first-party gateway uses the existing authenticated customer session: browser
requests use the credential-enabled API client; Android requests send the saved session
cookie. Overrides on another origin receive no DilaDrive session cookie.

New endpoints:

| Endpoint | Contract |
| --- | --- |
| `GET /api/locations/search?q=...` | `{ "results": [{ "address": "...", "latitude": number, "longitude": number }], "attribution": "..." }` |
| `GET /api/locations/reverse?latitude=...&longitude=...` | `{ "address": "...", "latitude": number, "longitude": number }` |

Both require a signed-in CUSTOMER session. They return formatted addresses and real
provider coordinates, with finite/range/length validation. Empty searches stay empty;
invalid responses, timeouts, provider failures and rate limits return errors, never fake
locations. Flutter validates the gateway response again. Selecting a suggestion uses
the existing `_setLocation` code to update the address, coordinates and booking draft,
invalidate a stale route/fare and rebuild the map. A destination selected before pickup
now retains the correct destination icon.

## Provider and usage policy

Development uses the public Nominatim service, with no API key. This supports modest,
user-triggered place searches, not autocomplete or bulk geocoding. The gateway:

- Uses an application-specific User-Agent.
- Shares one limiter across all users, search and reverse requests, spacing uncached
  requests by at least 1.1 seconds. It rejects excess requests rather than queuing a burst.
- Caches validated responses, including empty results, for 24 hours in a bounded
  512-entry in-memory cache. Cache hits do not make an upstream request.
- Backs off for one minute if the provider returns HTTP 429.
- Receives Nominatim requests only when the user submits Search, selects a map point, or
  uses GPS. Typing uses a separate, debounced Photon endpoint. No bulk geocoding is added.
- Displays linked OpenStreetMap attribution in the search page as well as on the map.

The public limit applies to the entire application, not each backend instance. Use a
single development gateway. Before scaling to multiple instances or substantial traffic,
configure a self-hosted/managed Nominatim-compatible endpoint or a shared rate-limited
gateway that meets the provider's terms. The endpoint can be changed on the server
without rebuilding Flutter.

References: [Nominatim policy](https://operations.osmfoundation.org/policies/nominatim/),
[search API](https://nominatim.org/release-docs/latest/api/Search/),
[reverse API](https://nominatim.org/release-docs/latest/api/Reverse/).

## Map tiles

The existing OSM tile URL is retained. HTTPS, no subdomains, no retina simulation,
viewport-only loading (`panBuffer: 0`), the actual app identifier and visible linked
copyright attribution remain configured. No warning or assertion is suppressed.

Inspection of the installed flutter_map 8.3.2 NetworkTileProvider confirms that its
BuiltInMapCachingProvider persists native tiles and uses HTTP cache freshness headers
by default. Chrome uses browser caching and the page-origin Referer; the web page already
sets `strict-origin-when-cross-origin`. No cache-disabling headers, freshness overrides,
prefetch tools or offline tile downloads are added. No extra caching package is needed.

The generic flutter_map OSM debug reminder still appears: the library prints it whenever
the OSM hostname is used. It is distinct from a configuration error. Public tiles are
capacity-limited and have no service guarantee; `MAP_TILE_URL` remains available for a
suitable deployment provider. See the [tile policy](https://operations.osmfoundation.org/policies/tiles/).

## Run and configure

Restart Spring Boot after compiling these changes, then fully restart Flutter (a hot
reload does not update Dart defines). Sign in as a customer before searching.

Default Chrome and Android emulator development need **no geocoding key and no location
URL define**: existing API defaults are localhost:8080 on desktop Chrome and 10.0.2.2:8080
on the Android emulator.

Optional Flutter defines:

- `API_BASE_URL`: backend origin. Physical Android devices need the development PC's
  reachable LAN address or a deployed HTTPS backend, rather than localhost/10.0.2.2.
- `BOOKING_LOCATION_API_URL`: complete gateway base URL ending in `/api/locations`, or
  an alternative gateway implementing the same contract. Omit it to reuse API_BASE_URL.
- `MAP_TILE_URL`: retain the existing tile-provider override if needed for deployment.

Backend environment variables:

| Variable | Default / purpose |
| --- | --- |
| `BOOKING_GEOCODING_URL` | `https://nominatim.openstreetmap.org`; a Nominatim-compatible base URL. |
| `BOOKING_GEOCODING_USER_AGENT` | `DilaDrive/0.1 (transport booking)`; use a stable app identity with your real contact details for deployment. |
| `BOOKING_GEOCODING_COUNTRIES` | `my`; comma-separated country codes, or an empty value for unrestricted search. |

Internet access is required. Browser GPS needs localhost or HTTPS and user permission.
For a deployed web origin, allow that specific origin in the existing backend CORS
configuration; loopback development origins are already allowed with credentials.
Use HTTPS for both a deployed web page and its backend to avoid mixed-content blocking.

This machine's live JVM check used the Windows trusted certificate store. If your Java
installation reports a PKIX certificate error, configure its trust store correctly;
do not disable certificate verification.

Road route preview remains separate from geocoding and continues to require the existing
`BOOKING_ROUTING_URL` configuration. GPS, markers, the DilaDrive theme and Trip Details
navigation are retained. No database migration is needed.

## Verification

- New backend tests: **12 passed** (provider HTTP parsing, coordinates, reverse, empty
  results, cache hits, shared rate limit, upstream backoff, errors, customer authorization
  and Flutter response contracts).
- Complete backend regression suite: **180 passed**, with the optional live test skipped.
- Opt-in live check: **passed**, making one real public-landmark search and one reverse
  request with policy-compliant spacing. No location results are hardcoded in the app.
- Actual Flutter provider/search/map source in the dependency-isolated harness:
  **13 tests passed**, covering UTF-8 addresses, default URL/session handling, custom gateways, GPS
  delegation, invalid coordinates, rate-limit messages, search selection, manual fallback,
  keyboard layout, destination-first markers and marker updates.
- Ran dart format and full flutter analyze. No new diagnostics; the pre-existing unused
  `bookings.dart` import in customer `profile.dart:11` remains outside this task.
- `flutter build web --no-pub`: passed, including the Wasm compatibility dry run.
- The Chrome test runner was attempted but its Windows local server returns 404 for
  CanvasKit assets, preventing test startup. Runtime inspection confirmed that renderer
  loading failure, rather than a location exception. The stalled test browser was closed.
  Full-screen Chrome and physical Android GPS/permission/network checks remain manual.

## Files

Created: backend `LocationController.java`, `LocationGatewayService.java`, their two
test classes, optional `LiveLocationGatewayTest.java`, Flutter
`test/booking_route_map_test.dart`, and this guide.

Modified: backend `application.properties`; Flutter `booking_location_service.dart`,
`booking_location_page.dart`, `booking_location_search_page.dart`, `booking_route_map.dart`
and `test/booking_location_selection_test.dart`; the existing booking/runtime reports
were corrected to describe the current gateway and native cache behavior.

Admin, Driver, payment, assignment and database functionality were not changed.
