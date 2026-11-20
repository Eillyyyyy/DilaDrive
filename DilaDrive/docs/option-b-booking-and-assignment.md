# Final booking policy: Option B — pending without reservation

This is the current business policy and supersedes earlier documentation describing temporary customer vehicle holds. Driver–Vehicle **Option A remains unchanged**: one current registered vehicle belongs to one driver. Booking Option B and Driver–Vehicle Option A are separate decisions.

## Implemented behavior

Customer location search, GPS, map pins, real driving route, Trip Details, Availability, Fare, Review and My Bookings remain in the existing flow and theme. No routing or map provider was replaced.

The customer availability/estimate request uses the configured driving route and database capacities. Small/medium/large bags contribute 1/2/3 luggage points. Existing `small_count`, `medium_count`, `large_count`, `luggage_units`, `vehicle_capacity`, and `vehicle_luggagecapacity` fields are reused; no duplicate capacity columns were added.

Vehicle candidates must have sufficient passenger and luggage capacity, an operational status, valid recorded vehicle certificates for the pickup date, no maintenance overlap, and no overlapping active confirmed vehicle assignment. The existing operational status `AVAILABLE` is accepted as a legacy equivalent of `ACTIVE`; neither is changed to BUSY merely because of a future assignment. Actual capacities come from PostgreSQL, never model-name assumptions. Fleet administrators must record realistic luggage point capacity with passenger seats occupied.

Candidates are ordered by smallest sufficient passenger capacity, then luggage capacity, then backend-calculated fare, then a stable ID. Distinct vehicle types are returned in that order. Fare rates remain company data in `booking_fare_rate`. The recommendation contains type/capacities, not a reservation or assigned vehicle ID. Fare refresh now updates the draft recommendation and fare together so a newly available alternative cannot leave the old recommendation on the summary.

Submission saves **PENDING**, authenticated customer contact details, geocoded trip details, bag counts/points, road distance/duration, estimated fare and recommended type. It saves **no driver ID, vehicle ID, hold or assignment**. It uses the existing idempotency key mechanism. Two requests for the same available slot can both be submitted, including concurrently. Admin and customer receive persisted notifications.

The customer sees: “Your booking request has been submitted. Vehicle availability and final confirmation are subject to Admin approval.”

## Schedule and assignment rules

All booking wall-clock values follow the existing **Asia/Kuala_Lumpur** convention. The planned minimum end is pickup + actual driving duration + `BOOKING_BUFFER_MINUTES` (default 30). This buffer also provides the configured allowance for repositioning; geographic repositioning between arbitrary successive routes is not separately calculated.

Intervals overlap only when `requestedStart < existingEnd AND requestedEnd > existingStart`. Midnight crossings work; exactly adjoining intervals do not overlap. Active trip statuses are ASSIGNED, OTW, WAITING, PICKUP and DROP, associated with a CONFIRMED booking (legacy in-progress booking status spellings are also recognized). Pending requests and obsolete hold rows never block resources. COMPLETED, CANCELLED and SUPERSEDED history does not block future trips. Unknown legacy active schedules are conservatively rejected for manual correction rather than inventing end times.

Admin chooses a suitable available vehicle and explicitly selects its eligible registered driver. Both pages use the same shared resource picker and server preview. Driver eligibility includes ACTIVE account, operational availability, the existing current vehicle relationship, no overlapping active assignment and no recorded unavailable interval. Maintenance and driver unavailable periods are checked separately.

Confirmation locks the booking, then the selected driver and linked vehicle using existing pessimistic PostgreSQL row locks. It repeats status, capacity, maintenance, operational, certificate, driver and schedule checks **after acquiring locks**. The assignment insert and booking CONFIRMED update commit atomically. Competing confirmations for the same resource serialize; the loser sees the committed conflict and receives **409** without an assignment or partial confirmation. A unique partial database index permits only one nonterminal assignment per booking. This follows PostgreSQL's row-lock behavior: https://www.postgresql.org/docs/current/explicit-locking.html .

Customer recommendations do not require an eligible driver to be reserved; Admin performs that separate eligibility check. An alternative suitable type may be assigned even if it differs from the stored recommendation. The stored recommended type remains the estimate reference, while the actual driver and vehicle come from `driver_assignment`.

## Lifecycle and integration

- **PENDING:** request only; no resource allocation. Availability can change before review.
- **CONFIRMED:** eligible driver and vehicle explicitly assigned. Trip progression remains on the assignment, while the booking stays CONFIRMED.
- **REJECTED:** a nonblank reason is required and stored; customer is notified.
- **CANCELLED:** cancelling an active assignment marks the assignment and booking cancelled, releases their interval, and preserves the row. Existing customer cancellation restrictions remain in place.
- **COMPLETED:** terminal assignment and booking; history retained and future availability released.
- **Reassignment:** old assignment becomes **SUPERSEDED** with its original driver/vehicle/time intact; a new ASSIGNED record receives a new assignment ID. Customer and new driver are notified; the old driver receives a persisted change notice and optional push. The booking links update to the replacement resources. Superseded records are not treated as active or shown as current driver trips.

Bookings and Assignments both read the same backend assignment records. Existing assignment-change and focus refresh mechanisms remain. Pending/rejected requests are excluded from driver trip responses even if inconsistent legacy assignment rows exist. Driver trip details now receive bag quantities and luggage points from the same assignment response used by Admin.

Confirm & Assign requires a pending booking. The existing explicit repair for a previously confirmed legacy booking without an assignment uses `POST /api/assignments`, rather than reconfirming it. Inconsistent historical records are retained for review, not silently confirmed or erased.

## Database migration

`database/schema/024_pending_without_reservation.sql` is a transactional forward migration after 023. It adds `driver_unavailable_period` and its resource/time index, and replaces the existing active-booking assignment index predicate to exclude SUPERSEDED history. It adds no duplicate Booking, Assignment, Trip, capacity or luggage columns. The old hold table/rows remain for historical compatibility but are no longer read or written by customer availability/submission/editing.

Applied successfully to the configured local database on 17 November 2026. Counts before/after were unchanged: **7 bookings, 2 assignments, 5 vehicles, 6 drivers**. Test fixtures were confined to disposable test schemas, which were removed after testing.

For another database, use its normal PostgreSQL credentials and apply:

```powershell
psql -h localhost -U postgres -d DilaDrive -v ON_ERROR_STOP=1 -f diladrive/database/schema/024_pending_without_reservation.sql
```

Run from the repository root; adjust connection parameters for that deployment. Do not rerun baseline schema creation on an existing database. Maintain normal database backups.

## API changes

- **Added:** Admin-only `GET /api/bookings/{id}/assignment-options?scheduledEndAt=...`. Returns suitable pairs with vehicleId, vehicleType, vehicleModel, vehiclePlate, passengerCapacity, luggagePointsCapacity, driverId and driverName. Omitting the end uses the stored duration + buffer end. Preview does not reserve anything.
- **Changed behavior:** existing `POST /api/bookings/customer` and `/customer/complete`, plus complete editing, never create vehicle holds.
- **Preserved:** `POST /api/bookings/availability`, `/estimate`, `/route`, complete request DTOs, authenticated ownership and backend fares.
- **Preserved paths:** Admin Confirm & Assign and reassignment endpoints. Reassignment returns the **new** assignment ID. Invalid/conflicting resources return 409.
- **Added response fields:** assignment summaries include smallCount, mediumCount, largeCount and luggageUnits for Admin/driver requirements.
- **Retired unsafe HTTP adapters:** authenticated `GET /api/bookings/availability` and `GET /api/bookings/availability/recommendation` return **410** directing callers to POST with coordinates and bag quantities. These GET endpoints had conflicting exact-start/no-driving-duration logic and are unused by the current Flutter and React flows. Internal legacy Java compatibility adapters are retained.

## Validation actually run

| Check | Result |
| --- | --- |
| Full backend tests plus PostgreSQL customer-trip integration | **220 discovered; 217 passed, 3 optional live-provider tests skipped; no failures** |
| Final assignment/notification/schedule recheck | **39 passed** (24 PostgreSQL lifecycle + 15 service tests) |
| Final pending-driver-visibility recheck | **24 PostgreSQL integration tests passed** |
| Backend package | **Passed** |
| Flutter analyze after all Dart changes | **No issues found** |
| Dart formatting | Modified Dart files formatted |
| Isolated Flutter harness using actual source via lib junction | **36 passed**: 23 location/route widget checks plus booking contract and driver model checks |
| Normal Flutter test command | **Blocked before tests run:** existing pdfium_dart native asset hook requires a Windows C compiler |
| Booking/assignment React tests | **11 passed** |
| Full React test suite | **27 passed, 1 unrelated failure** in `tests/driver-forms.test.cjs`: “editing without a vehicle renders a valid profile form”, existing Driver Management text assertion |
| React production build | **Passed** |

The PostgreSQL tests use real persistence and the Spring MVC request pipeline. Routing is stubbed in these lifecycle tests to isolate scheduling from public service availability; this report does not claim another live routing-provider test. The existing road-router tests remain in the regression suite. UI rendering/contract tests do not constitute browser or Android device end-to-end testing.

The 18 requested business scenarios are covered by the existing and expanded service/integration tests: capacity and weighted bags; passenger/luggage insufficiency; maintenance; confirmed interval conflicts; ignored pending requests; two customers for one slot; atomic first confirmation; HTTP 409 on conflicting second confirmation; alternative type success; independent driver conflict; rejection reason; cancellation release; preserved reassignment history; shared assignment listing; customer CONFIRMED status; driver mine endpoint; idempotency and concurrent duplicate prevention. Additional cases cover midnight, adjacent intervals, completion release, stale holds, driver leave and fare tie-breaking.

Repeat backend checks from `backend/diladrive-api`:

```powershell
$env:RUN_CUSTOMER_TRIP_IT='true'
mvn '-Dtest=*Test,CustomerTripPostgresIT' '-Dnet.bytebuddy.experimental=true' test
Remove-Item Env:RUN_CUSTOMER_TRIP_IT
```

Java 25 on this machine needs the Byte Buddy experimental option for test tooling. Maven here is installed at `$env:USERPROFILE/.maven/maven-3.9.15/bin/mvn.cmd`; use that path if mvn is not on PATH. Tests need PostgreSQL access to create/drop their unique disposable schemas.

## Source files changed for this task

Backend paths below are relative to `backend/diladrive-api/`:

- `src/main/java/com/diladrive/service/CustomerTripService.java`
- `src/main/java/com/diladrive/service/AssignmentServiceImpl.java`
- `src/main/java/com/diladrive/service/BookingService.java`
- `src/main/java/com/diladrive/service/VehicleService.java`
- `src/main/java/com/diladrive/repository/AssignmentRepository.java`
- `src/main/java/com/diladrive/repository/AssignmentSummaryProjection.java`
- `src/main/java/com/diladrive/dto/AssignmentResponse.java`
- `src/main/java/com/diladrive/controller/BookingController.java`
- `src/main/java/com/diladrive/controller/BookingConfirmationController.java`
- `src/main/resources/application.properties` (removed obsolete hold duration setting)
- `src/test/java/com/diladrive/CustomerTripPostgresIT.java`
- `src/test/java/com/diladrive/LiveRoadRouteTest.java` (include latest schema migration in opt-in fixture setup)
- `src/test/java/com/diladrive/service/AssignmentServiceTest.java`
- `src/test/java/com/diladrive/service/CustomerBookingCreationTest.java` (terminal-history expectations)

Other source paths are relative to the `diladrive/` project folder:

- `database/schema/024_pending_without_reservation.sql`
- `mobile-app/lib/services/booking_service.dart`
- `mobile-app/lib/models/driver_trip.dart`
- `mobile-app/lib/screens/customer/booking/booking_details_page.dart`
- `mobile-app/lib/screens/customer/booking/booking_availability_page.dart`
- `mobile-app/lib/screens/customer/booking/booking_review_page.dart`
- `mobile-app/lib/screens/driver/trip_details.dart`
- `mobile-app/test/booking_backend_contract_test.dart`
- `mobile-app/test/driver_trip_test.dart`
- `web-admin/src/components/AssignmentResourcePicker.jsx`
- `web-admin/src/pages/Bookings.jsx`
- `web-admin/src/pages/Assignments.jsx`
- `web-admin/src/services/bookingAssignments.js`
- `web-admin/tests/assignment-pair.test.cjs`
- `web-admin/tests/booking-assignments.test.cjs`
- `docs/option-b-booking-and-assignment.md`

Build/test commands also generated local outputs in target/dist and the existing ignored Flutter harness. Existing app_theme.dart and working map/location source were preserved.

## Running and remaining checks

The rebuilt local backends were restarted with the local routing profile on **8080** and the previously available **8081**. Sign in again because restart invalidates in-memory sessions. Future local startup remains `./run-local.ps1 -UseWindowsTrust` from the backend folder, or the existing local VS Code profile. Apply migration 024 before starting this version against another database.

Existing required settings remain `BOOKING_ROUTING_URL` (deployment router), company `booking_fare_rate` rows, realistic recorded capacities, database connectivity and deployment CORS origins. `BOOKING_BUFFER_MINUTES` controls the shared road-duration buffer. Record maintenance in vehicle_unavailable_period and driver leave/operational intervals in driver_unavailable_period. Dedicated management screens for these period tables were not added.

Persisted notifications work without FCM. For actual background push delivery, enable `BOOKING_PUSH_ENABLED=true`, configure `FIREBASE_PROJECT_ID` and server application-default credentials (`GOOGLE_APPLICATION_CREDENTIALS`), and ensure the Android clients register valid device tokens and allow notifications. Push delivery to actual phones was not verified.

Manual browser/Android verification remains: submit two matching requests, confirm one, recheck the second, choose an alternative or reject it, inspect Assignment history, open the customer booking and driver trip, then cancel/reassign and refresh. Android and browser visual interaction were not claimed as tested. Installing the existing PDF dependency's required Windows C/C++ build tooling is still needed to run the app's complete native Flutter test suite. The unrelated Driver Management React test failure remains outside this booking task.
