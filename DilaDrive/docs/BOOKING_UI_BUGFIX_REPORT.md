# DilaDrive booking UI bugfix report

## Causes and fixes

| Issue | Cause and fix |
| --- | --- |
| Analog time picker | The create flow already generated half-hour pickup slots, but its list reopened at midnight and flight time still used showTimePicker. Pickup now scrolls to the selected slot or the first remaining slot, highlights the selection, and asks for another date when today's slots are exhausted. The flight sheet uses independent hour, minute and AM/PM wheels with all 60 minutes. The existing pending edit flow also uses the wheel while retaining its existing timing rules. |
| Review overflow | The sheet extent budget excluded the protected bottom inset, while the footer still consumed fixed padding. The sheet minimum now includes the bottom safe area. The footer and its padding share one height budget, and the remaining space goes to one scroll view using the DraggableScrollableSheet controller. Mouse dragging is supported for Web. |
| Confirm Booking RangeError | On Dart Web, the JavaScript bit shift used for 1 << 32 evaluates to zero, so Random.nextInt received an invalid bound. Key generation was also outside the try/finally that unlocks submission. UUID v4 generation now occurs inside that block. Retries of the same trip and accepted quote reuse their key. Returning from Review preserves the submitted quote token when a fresh eligibility check returns the same category and fare. The existing backend keys requests by customer and payload, so changes to the submitted trip or quote use their own key. |
| Grey map | The prior sheet could leave too little usable camera space, including at maximum extent, and tiles had no visible recovery action. The sheet now retains more map clearance, the map fits after layout using the current sheet padding, and a stable TileProvider avoids recreating clients on sheet rebuilds. A tile error exposes a retry action while the existing polyline, markers and map controller remain mounted. A live provider outage was not reproduced. |
| Login 401 | The mobile login endpoint returns 401 when its credential and role lookup fails. Flutter handles that response as invalid credentials. Successful login creates a role-scoped JSESSIONID. Chrome sends the cookie using a credentialed BrowserClient and Android sends the stored session cookie. Protected booking endpoints handle an expired session separately and send the customer back to sign in. |
| Review readability | The review uses separate cards for customer, locations and route, pickup date/time, optional flight details, passengers and luggage, vehicle preference, and estimated fare. Vehicle and fare values come from the accepted backend quote. |

## Files and dependencies

- Added booking_time_sheet.dart. Updated booking_trip_details_sheet.dart and booking_details_page.dart to use the reusable picker sheets.
- Updated booking_map_sheet.dart, booking_location_page.dart, booking_vehicle_page.dart, booking_review_page.dart and booking_route_map.dart.
- Promoted the UUID package already locked at version 4.6.0 to a direct dependency in pubspec.yaml and pubspec.lock.
- Added booking_review_sheet_layout_test.dart. Updated booking_trip_details_sheet_test.dart, booking_map_sheet_flow_test.dart, booking_route_map_test.dart, booking_review_retry_test.dart and auth_service_test.dart.
- Backend and database changes for this bugfix are zero. A deployment that has not yet applied the previous flight-details migration still needs migration 029.

## OpenStreetMap and production tiles

The default development template uses HTTPS OpenStreetMap raster tiles with linked attribution, the existing app-specific native user agent, browser referer policy and HTTP-aware caching. For production, configure MAP_TILE_URL and MAP_TILE_ATTRIBUTION for the team's licensed provider or self-hosted service. Public OSM tiles are operated on a best-effort basis and have usage restrictions. [OpenStreetMap Foundation Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/)

## Verification

- Flutter analysis completed with no issues. All 25 focused Flutter tests passed, including retry with the same key, session expiry, account verification and the preserved quote token when returning to Review.
- The ordinary Windows Flutter test run stopped at the existing PDFium hook because the host lacks a supported C compiler. The suite in a temporary copy with PDF build hooks omitted finished with 172 passing and three existing failures in emulator API configuration and two dashboard assertions.
- The responsive widget tests exercised 360 × 800, 390 × 844, 412 × 915, 840 × 866 and 1440 × 900, with safe padding and a deliberately constrained keyboard viewport. The review content reached its final fare, the action stayed reachable, and no overflow was reported.
- The map test used local tiles and a controlled tile error to avoid automated requests to the public OSM servers.
- A Dart-to-JavaScript probe in headless Chrome reproduced the zero shift result and generated two valid, distinct UUID v4 keys.
- Maven passed 64 tests, including 44 cases on a disposable PostgreSQL schema. Checks covered authentication, CORS, customer verification, conflicting vehicle assignments, booking persistence and idempotency.

## Remaining limitations

The Flutter Chrome widget runner stalled during loading and was stopped. The viewport tests and headless Chrome UUID probe provide separate coverage. Live Android interaction, an end-to-end Web session using the deployment hostname and the production tile provider remain manual checks.

## Manual Android and browser checks

1. Point the app at the development backend. Use a verified customer and keep the browser and API on the same hostname when testing session cookies.
2. Select pickup and destination. Drag the sheet and check that both markers, the road route and attribution remain visible. Temporarily interrupt the tile connection and try the map retry action.
3. Pick a date and a half-hour pickup slot. Reopen it and verify the selected row is visible. Select an exact flight time such as 08:17, go back and check the pickup time remains unchanged.
4. Enter a long address and notes. Drag and scroll Review to the fare with the keyboard shown and dismissed. Confirm the action remains reachable.
5. Submit once and check that the server returns a pending booking reference. Test a recoverable transport failure using the same draft and verify retry does not duplicate it. Try invalid credentials and an expired session separately.
