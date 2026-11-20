# Booking location Material and tile configuration

## Cause and fix

Flutter paints ListTile ink on its closest Material. The location panel previously used
an opaque white Container/DecoratedBox between the pickup/destination tiles and the
Scaffold's Material. That decoration concealed the ink and triggered the diagnostic.

The panel is now a white Material with the original 24-pixel rounded top corners,
anti-aliased clipping and the same inner padding. Both location tiles and the panel's
other controls paint on that surface. Search suggestions already had Material through
Card; those Cards now clip ink to their rounded edges. The map selection bottom sheet
already supplies a Material ancestor with no intervening opaque decorated parent.
No Flutter errors or assertions are filtered or suppressed.

## OpenStreetMap development configuration

The existing public OSM tile provider is retained for modest, interactive Chrome
development use. The configuration uses:

- The official HTTPS tile URL, without subdomains or retina simulation.
- `panBuffer: 0`, so additional offscreen tile rows are not prefetched.
- Native zoom limit 19 and the actual existing Android application ID
  `com.example.diladrive_customer` for native identification.
- Visible, clickable `© OpenStreetMap contributors` attribution linking to
  `https://www.openstreetmap.org/copyright`.
- Normal browser HTTP caching and browser-supplied User-Agent/Referer; the web document
  explicitly uses `strict-origin-when-cross-origin`. No no-cache headers, custom browser
  User-Agent overrides or bulk/offline download behavior are added.

The installed flutter_map 8.3.2 prints its generic public-server reminder in debug mode
whenever it detects the OSM URL, even with an application ID configured. That reminder
is intentionally retained. It is separate from the ListTile diagnostic.

See the [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/).
Public tiles have limited capacity and no availability guarantee. Keep the existing
`--dart-define=MAP_TILE_URL=...` override for deployments needing another endpoint.
This change checks browser development usage, not a production-volume service agreement.
Inspection during the subsequent geocoding integration confirmed the installed
NetworkTileProvider enables BuiltInMapCachingProvider by default on native platforms.
It persists tiles and honors HTTP freshness headers; the older TileLayer inline comment
describing session-only caching is stale. No extra cache plugin or freshness override
is needed. Browsers continue using their own HTTP cache.

## Files

- `mobile-app/lib/screens/customer/booking/booking_location_page.dart`: panel Material.
- `mobile-app/lib/screens/customer/booking/booking_location_search_page.dart`: Card clipping.
- `mobile-app/lib/widgets/booking_route_map.dart`: tile options and linked attribution.
- `mobile-app/web/index.html`: explicit browser referrer policy.
- `mobile-app/pubspec.yaml` and `pubspec.lock`: url_launcher promoted from an existing
  transitive dependency to an explicit dependency for the attribution link.
- `mobile-app/test/booking_location_material_test.dart`: regressions for panel rendering,
  both searches, selection, GPS, route preview and navigation to Trip Details.
- This report.

## Verification

- Ran dart format on the three changed Dart source files and the new test.
- Full flutter analyze: no new issues. The existing unused `bookings.dart` import in
  `screens/customer/profile.dart:11` remains outside this task.
- `flutter build web --no-pub`: passed, including the Wasm compatibility dry run.
- Existing dependency-isolated location suite: **6 tests passed**, including real search
  suggestion widget selection, lookup failure/manual confirmation and keyboard layout.
  The harness imports the actual location service and search page source.
- Attempted the new Material regression tests plus existing location/booking tests with
  `flutter test --platform chrome`. Flutter's native-asset prebuild fails before any tests
  start because the unrelated existing pdfium_dart dependency needs a Windows C compiler.
  Disabling native assets for that test process also cannot run the suite: pdfium_dart
  and objective_c require that feature. No global Flutter configuration was changed.

Full-screen browser runtime verification is still required. After a full reload in Chrome,
open Create Booking, tap both location tiles, select search suggestions, open both map
selection choices, use current location, preview a route and continue to Trip Details.
Check that no ListTile diagnostics recur and that the attribution link opens correctly.
For tile-policy checks, use normal browser caching (DevTools "Disable cache" unchecked)
and inspect tile requests for the page-origin Referer and normal cache reuse.

No Admin, Driver, backend, database or app_theme.dart changes were made for this fix.
