# Customer create booking

The existing Flutter booking pages, mobile sessions, Booking/Vehicle/Customer repositories,
driver_assignment relationship, Option A driver–vehicle pairing, notifications and customer
booking list are reused. Customer creation now uses the complete trip DTO at both
`POST /api/bookings/customer` and `/api/bookings/customer/complete`. The older DTO remains
an internal compatibility adapter, with no HTTP mapping that bypasses routing or pricing.
Existing Admin creation and legacy booking edit workflows remain available.

## Configuration and database

`database/schema/023_customer_booking_flow.sql` is an additive, repeatable migration after
022. It was applied to the configured **local development database**. No company records
were removed. Apply it separately to other environments before starting the new backend;
Hibernate remains configured to validate the schema.

New booking columns store coordinates, small/medium/large quantities, luggage units,
road duration, buffered end time, contact name/phone snapshots, rejection reason and fare
snapshot. New tables: `booking_fare_rate`, `booking_vehicle_hold`,
`vehicle_unavailable_period`, `mobile_push_token`. Existing assignments are reused.

Set backend environment variables:

| Variable | Purpose |
| --- | --- |
| `BOOKING_ROUTING_URL` | Base URL of a company-hosted OSRM-compatible driving router; omit `/route/v1/driving`. No default public demo server or fake distance is used. A gateway can supply provider authentication without embedding credentials in Flutter. |
| `BOOKING_BUFFER_MINUTES` | Repositioning/return buffer after road travel; default 30. Choose a value appropriate to the service area. |
| `BOOKING_HOLD_MINUTES` | Pending reservation lifetime; default 15. |
| `BOOKING_PUSH_ENABLED` | Set `true` to enable FCM delivery; default false. |
| `FIREBASE_PROJECT_ID` | Firebase project for the existing mobile app. |
| `GOOGLE_APPLICATION_CREDENTIALS` | Path to a server service-account credential file, outside source control, with FCM permissions. |

Enter **approved company rates**, not the example rates from the task, in
`booking_fare_rate(vehicle_type, base_fare, rate_per_km, additional_charge, active)`.
Vehicle type matching is case insensitive. Missing/inactive pricing blocks quoting and
creation with a meaningful error. No pricing records are seeded by the migration.

The vehicle category in PostgreSQL defines passenger capacity and boot capacity
with all category seats occupied. Small, medium, and large bags use 1, 2, and
3 luggage units respectively. Bag counts are independent of passenger count.
The admin vehicle forms show both capacities as read-only values.

Chrome and Android now share the authenticated backend geocoding gateway, while GPS
and permissions stay on the device. `BOOKING_LOCATION_API_URL` remains an optional
override; see [location gateway setup](location-geocoding-gateway.md).
The map uses flutter_map, with `MAP_TILE_URL` available as a Dart define. Its default is
OpenStreetMap tiles with attribution and the app user-agent; configure a suitable tile
provider for production volume. Search suggestions and map taps save coordinates and
formatted addresses. A manually entered address without valid coordinates cannot proceed.

Provider references: [OSRM route API](https://project-osrm.org/docs/v26.4.0/http),
[flutter_map options](https://docs.fleaflet.dev/usage/options),
[Firebase server credentials](https://firebase.google.com/docs/admin/setup).

## API and flow

All customer endpoints require the existing role-scoped mobile session cookie. Customer
ownership always comes from the session, never a submitted customer ID.

| Method and endpoint | Use |
| --- | --- |
| `POST /api/bookings/route` | Road distance, duration and GeoJSON coordinate pairs for preview. |
| `POST /api/bookings/availability` | Capacity/time suitability and fare quote. |
| `POST /api/bookings/estimate` | Same complete quote used by Flutter. Optional `?bookingId=` checks ownership and excludes that pending booking's own hold during editing. |
| `POST /api/bookings/customer` | Canonical complete booking creation. Requires `Idempotency-Key`. |
| `POST /api/bookings/customer/complete` | Creation alias used by Flutter. |
| `PUT /api/bookings/{id}/customer/complete` | Re-route, re-price and replace an owned pending booking and its hold atomically. |
| `GET /api/bookings/mine` | Existing database-backed customer booking list, extended with saved quantities, coordinates and contact snapshots. |
| `POST /api/bookings/{id}/cancel` | Existing owned cancellation; cancelled holds no longer block availability. |
| `POST /api/bookings/{id}/reject` | Admin-only rejection with required `reason`; releases the hold and creates a customer notification. |
| `POST /api/bookings/{id}/confirm-and-assign` | Existing Admin confirmation and Option A assignment, now also checks holds, maintenance and minimum buffered end time. |
| `POST/DELETE /api/mobile/push-token` | Register/remove a device token for the authenticated customer or driver. |

Trip JSON fields: `pickupAddress`, `destinationAddress`, `pickupLatitude`,
`pickupLongitude`, `destinationLatitude`, `destinationLongitude`, `scheduledAt`,
`passengerCount`, `smallCount`, `mediumCount`, `largeCount`, `tripType`, optional
`flightNumber` and `additionalNotes`. `scheduledAt` is Malaysia local time. Airport pickup
uses the selected landing time as the requested service start. Flight number is optional.

Location preview must succeed before continuing. Trip Details clears stale availability
and fare whenever submitted. Availability and fare screens obtain fresh backend quotes.
Review prevents repeated taps, retains a retry key and shows the saved booking reference.
Going backward keeps the draft. New saved bookings also use the complete flow when edited;
legacy bookings retain their existing editor.

## Availability, holds and fares

Suitable vehicles have operational status AVAILABLE (the existing fleet convention) or
ACTIVE, enough passenger seats and boot units, and valid recorded road-tax/service
dates. Maintenance status and unavailable periods exclude them. The recommendation sorts
by seats, boot capacity and an internal stable tie-breaker; customers see vehicle types
and capacities, without registration numbers, individual vehicle IDs or driver selection.

The slot is `[pickup, pickup + road duration + buffer)`. Conflicts use
`newStart < existingEnd && newEnd > existingStart`. Active assignments, confirmed legacy
bookings without assignment rows, maintenance periods and unexpired pending holds block
overlapping slots. Cancelled/rejected bookings do not. Unknown legacy assignment schedules
are treated conservatively. A completed/cancelled assignment is no longer a reservation.

Creation re-routes, rechecks and re-prices inside a transaction. After routing, it locks
vehicle rows in stable ID order before checking and writing a short hold. This deliberately
serializes fleet reservation writes for this application's fleet size. Admin assignment
uses the existing driver/vehicle pessimistic locks and checks these same holds, excluding
the booking being assigned. Driver availability and assignment overlap are checked again.
An Admin-selected end cannot precede the routed duration plus buffer. Reassignment uses
the same validation. Different suitable vehicles can serve simultaneous bookings.

No driver or final vehicle is assigned during creation. The booking is PENDING; the
separate hold expires after the configured interval even if Admin has not reviewed it.
After expiration the request remains pending, without a capacity guarantee. Admin must
revalidate at assignment. Cancellation/rejection immediately stops the hold blocking;
rejection also removes its hold row. Confirmation makes the assignment authoritative.

Fare is `base + roadDistanceKm × ratePerKm + configuredAdditionalCharge`, calculated
with Java BigDecimal and HALF_UP rounding to two decimals. Flutter displays backend
values. The amount and quote snapshot are stored on the booking; subsequent pricing-table
changes do not rewrite historical fares. Admin can approve a different final amount through
the existing workflow. Editing a pending trip recomputes its estimate.

Database notifications are saved transactionally. FCM is sent asynchronously after commit
when configured. Push failure cannot roll back a booking; the persistent inbox remains
available. The current SDK integration supports existing registration tokens. Verify token
registration, refresh, sign-out and background delivery on the actual Android app.

## Files created

- Backend: `dto/TripQuoteRequest.java`, `controller/MobilePushController.java`,
  `service/CustomerTripService.java`, `service/RoadRoutingService.java`,
  `service/BookingPushService.java`.
- Tests: `CustomerTripPostgresIT.java`, `service/RoadRoutingServiceTest.java`,
  `mobile-app/test/booking_backend_contract_test.dart`.
- Database: `database/schema/023_customer_booking_flow.sql`.
- Flutter: `mobile-app/lib/widgets/booking_route_map.dart`.
- Documentation: this file.

## Files modified for this task

- Backend: `pom.xml`, `application.properties`, `BookingController.java`,
  `CustomerBookingResponse.java`, `Booking.java`, `BookingService.java`,
  `AssignmentServiceImpl.java`, `NotificationService.java`.
- Flutter: `pubspec.yaml`, `pubspec.lock`, `.gitignore`,
  `models/booking_draft.dart`, `models/customer_booking.dart`,
  `services/booking_service.dart`, `services/auth_service.dart`,
  `services/push_notification_service.dart`, the existing location/details/fare/review pages,
  `screens/customer/booking_details.dart`, `screens/customer/update_booking.dart`.
- Existing Flutter tests: `booking_flow_test.dart`, `booking_service_test.dart`,
  `booking_review_retry_test.dart`, `booking_session_expiry_test.dart`.
- Admin: `web-admin/src/pages/Bookings.jsx`.

Some of these files already had uncommitted work before this task; that work was retained.

## Verification and remaining manual checks

- Backend regression suite: 168 tests passed with
  `mvn -Dnet.bytebuddy.experimental=true test` on this machine's Java 25 runtime.
  Java 17 is the project's declared target and avoids that runtime compatibility flag.
- Isolated PostgreSQL suite: 17 tests cover capacity, mixed luggage, maintenance periods,
  holds/expiry, cancellation, rejection, pending persistence, retry, assignment, simultaneous
  confirmations, different vehicles at the same time, routing failure, owned editing,
  repricing, verified accounts, HTTP validation/session checks and My Bookings persistence.
  Run with `RUN_CUSTOMER_TRIP_IT=true` and `-Dtest=CustomerTripPostgresIT`. It creates and
  removes a schema named `diladrive_trip_check_<timestamp>`; it never truncates company data.
- Routing adapter: 3 tests exercise the actual HTTP parser against a local test server,
  including road distance/duration and no-route/no-configuration failure.
- Flutter service/contract tests: 10 passed using a dependency-only harness under
  `.booking_flow_check`, importing the actual application services. The full project's
  `flutter test` currently cannot build the existing PDF native dependency because this
  Windows host lacks a configured native C compiler. Updated widget tests remain available
  for a machine with that toolchain.
- Flutter analysis: no new booking diagnostics; the existing unused `bookings.dart` import
  in customer `profile.dart` remains outside this task.
- Admin production build passed. Booking/assignment tests: 10 passed. Broader Admin suite:
  26/27 passed; the unrelated pre-existing driver-form test expects wording absent from
  the current driver page.
- Android debug build passed: `mobile-app/build/app/outputs/flutter-apk/app-debug.apk`.
  Existing Firebase Kotlin-plugin and Android SDK-tool version warnings remain.

Configure routing, enter approved rates, restart the backend, then verify on Android:
GPS denied/disabled, address suggestions, both map pins, route preview, future times,
mixed bags, no availability, backward editing, fare/review, duplicate tapping, successful
PENDING submission, My Bookings, Admin confirmation/rejection and FCM delivery. No external
company routing endpoint, company rate data or Firebase service credentials were supplied,
so these live provider/device checks cannot be claimed as completed.
