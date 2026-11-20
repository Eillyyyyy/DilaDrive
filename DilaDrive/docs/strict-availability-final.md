# Strict availability + pending without reservation

This is the current policy. It extends the previous Option B implementation without changing Driver–Vehicle Option A, map/search/routing providers, Admin assignment records, or app_theme.dart.

## Diagnosis and actual fleet validation — 17 November 2026

The original API used a generic “No suitable vehicle” message for every empty candidate list. It did not distinguish insufficient capacity, schedule/maintenance/operational exclusions, and incomplete fleet configuration. The original failing request's exact schedule and bag quantities were not supplied, so its historical exclusion cannot be identified with certainty.

Read-only inspection found **five real vehicle records**, all with passenger and luggage capacities populated, and **zero booking_fare_rate rows**:

| Vehicle ID | Type | Passenger capacity | Luggage points capacity | Status | Road tax expiry | Service certificate expiry |
| --- | --- | ---: | ---: | --- | --- | --- |
| 1 | MPV | 6 | 3 | AVAILABLE | 2027-12-31 | 2027-06-30 |
| 2 | SEDAN | 4 | 2 | AVAILABLE | 2027-12-31 | 2027-06-30 |
| 3 | Mpv | 7 | 3 | AVAILABLE | 2026-11-17 | 2026-11-17 |
| 7 | Sedan | 4 | 3 | MAINTENANCE | 2027-08-27 | 2027-12-14 |
| 17 | Sedan | 4 | 2 | AVAILABLE | 2026-11-17 | 2026-11-17 |

The existing schema's AVAILABLE means operational; it is accepted alongside ACTIVE, rather than excluding the whole existing fleet for a naming mismatch. No capacities, status values, expiry dates, prices or vehicles were invented or updated.

After explicit user approval, the opt-in audit ran against the actual application data through Spring MVC, with both Hikari read-only mode and PostgreSQL `default_transaction_read_only=on`. It asserted `transaction_read_only=on` before requests. It used an existing approved customer identity in a test-only session; it did not bypass authentication in production or perform a browser login.

Controlled actual request:

- POST `/api/bookings/availability` (same handler as `/estimate`).
- UiTM Shah Alam → KLIA Terminal 1, using previously verified real geocoded coordinates.
- Pickup **2026-11-18 10:00 Malaysia time**.
- **4 passengers**, **1 small + 1 medium bag = 3 points**.
- Real OSRM route duration **3056 seconds** plus configured **30-minute buffer** gives occupied interval **10:00–11:20:56**.

Per-vehicle development diagnostics showed:

- **Vehicle 1 passes vehicle availability.**
- Vehicles 2 and 17 fail luggage points capacity.
- Vehicles 3 and 17 have expired certificates at the requested pickup date.
- Vehicle 7 is not operational because it is in MAINTENANCE.

The actual request therefore reaches pricing and returns:

```json
{"message":"Company pricing is not configured for MPV.","availabilityStatus":"SERVICE_ERROR"}
```

**Actual status: HTTP 503**, accurately reported as a service/configuration error, not “No suitable vehicle”. Company pricing is the remaining blocker for this otherwise capacity/schedule-suitable request. A separate real-fleet capacity-overflow request returned HTTP 200 with **CAPACITY_UNAVAILABLE**. This does not claim the original unspecified request had identical requirements.

Evidence is saved in `backend/diladrive-api/target/strict-availability-actual-request.json`, `strict-availability-actual-response.json`, and `strict-actual-fleet-audit.log`.

There are also two legacy terminal assignment rows with no scheduled end; their booking rows are PENDING. They do not reserve resources. Their historical status/times should be reviewed from business records; no end times or status corrections were guessed.

## API and filtering changes

Existing POST `/api/bookings/availability` and `/estimate` retain their existing fare/recommendation fields and now additionally return:

- `availabilityStatus`: AVAILABLE, CAPACITY_UNAVAILABLE, SCHEDULE_UNAVAILABLE or FLEET_NOT_CONFIGURED for evaluated results.
- `suitableVehicleCount`, `requiredPassengerCount`, `requiredLuggagePoints`.
- `occupiedStart`, `occupiedEnd`, `recommendedVehicleType`.
- Existing `suitableVehicleTypes` now groups type names case-insensitively.

No internal driver IDs, vehicle IDs or registration details are added to customer availability responses. Diagnostics with vehicle IDs remain in development logs only. `application-local.properties` enables DEBUG for CustomerTripService; production does not need this diagnostic setting.

SERVICE_ERROR is returned in dependency-failure error bodies, retaining HTTP 5xx. HTTP 500/503, network failure, malformed responses, or an old backend without the structured status are handled as SERVICE_ERROR in Flutter. Missing configuration or dependency errors are never converted into a successful empty availability response. Unknown results fail closed.

The same backend evaluator serves checking, pending edits and fresh submission checks. It uses recorded capacities and 1/2/3 luggage weights. Missing/invalid capacity or type configuration is excluded and reported; no model-based capacities are supplied. Operational/certificate/maintenance and confirmed interval checks run separately from capacity checks. Ranking uses smallest sufficient seats, then luggage points, then configured fare. Missing rates sort as unknown, never a made-up monetary value.

Company pricing still uses database BigDecimal base fare + road kilometres × rate/km + configured additional charges. Because this existing endpoint also provides the priced recommendation, missing company rates produce SERVICE_ERROR and prevent further progression. No speculative rates were inserted to get around this.

## Scheduling, completed history and pending requests

The occupied interval is pickup + actual road duration + BOOKING_BUFFER_MINUTES (default 30). Values follow the existing Asia/Kuala_Lumpur wall-clock convention. Overlap is strictly `requestedStart < existingEnd && requestedEnd > existingStart`, including midnight crossings; adjacent intervals do not overlap.

Pending, rejected, cancelled and superseded records do not block availability. Confirmed active assignments block their overlapping vehicle/driver intervals. Completed trips retain a **known recorded occupied interval**, which can block that same interval but not unrelated later slots. A completed legacy record with no usable end is retained for review and does not become an infinite future reservation. Unknown active schedules remain conservatively blocked until corrected.

Submission still authenticates ownership, requires the existing ACTIVE/verified customer status and contact data, validates the trip, recalculates route/luggage/fare, and rechecks current database availability. If no suitable vehicle remains it returns 409 without creating a booking. A successful request saves PENDING, recommended type and estimated fare, with **no driver ID, vehicle ID, assignment or vehicle hold**. Existing idempotency and duplicate-tap prevention remain.

Admin confirmation still repeats eligibility and overlap checks under PostgreSQL booking/driver/vehicle row locks, atomically inserts the assignment and updates CONFIRMED, and returns 409 on conflicts. Option A's linked driver/current vehicle relationship is preserved. Reassignment keeps SUPERSEDED history and creates the replacement record. Bookings, Assignments, driver trips and customer booking status continue to share the same persisted records.

## Flutter behavior

- AVAILABLE: recommended type/capacities, required passenger/luggage counts and **Available when checked**; Continue to Fare enabled only for a fresh successful check.
- CAPACITY_UNAVAILABLE: exact capacity message, Change Trip Details, Continue disabled.
- SCHEDULE_UNAVAILABLE: selected-time message, Change Date/Time, Continue disabled.
- FLEET_NOT_CONFIGURED: configuration message, Retry, Continue disabled.
- SERVICE_ERROR: “Unable to check vehicle availability. Please try again.” plus useful backend detail, Retry, Continue disabled.

A draft fingerprint covers locations, schedule, trip type, passenger/bag counts, route distance/duration and other trip details. Changing requirements, revising location details or retrying routing clears previous availability/fare. A draft-level request version prevents older requests, even from another service instance, from overwriting the latest check. Fare calculation and submission reject stale drafts. Fare parsing/failure cannot leave an enabled Summary action with invalid data.

The submission success message is now: “Your booking request has been submitted and is awaiting Admin confirmation. Vehicle availability is not reserved until your booking is confirmed.”

## Database changes

**No new migration or live business-data changes for this task.** Existing fields, pricing table, maintenance/driver unavailable tables and assignment index from prior migrations are reused. Actual fleet inspection/audit was read-only. Integration-test inserts/updates were confined to uniquely named disposable test schemas and cleaned up afterwards. No fake vehicles or prices were added to the application database.

## Test results

| Check | Actual result |
| --- | --- |
| Full backend suite + PostgreSQL customer-trip integration | **225 discovered, 221 passed, 4 optional tests skipped; no failures** |
| PostgreSQL lifecycle/strict integration within that run | **28 passed** |
| Approved read-only actual-fleet + live OSRM audit | **1 passed separately**, actual HTTP 503 SERVICE_ERROR confirmed |
| Isolated Flutter harness using actual app source | **52 passed**, including status recovery widgets, HTTP 503, incompatible backend, stale responses across service instances, changed date/passengers/luggage/route, booking contracts and location/route widgets |
| Flutter analyze | **No issues found** |
| Dart format | Modified Dart files formatted |
| Normal Flutter test command | Blocked before execution by existing pdfium_dart native hook: Windows C compiler not configured |
| Existing React booking/assignment tests | **11 passed** |
| React production build | **Passed** |

The normal full Flutter test suite therefore is **not claimed as passed**. Browser/Android visual end-to-end interaction and actual phone FCM delivery remain manual checks. The actual-fleet audit used Spring MVC and the real database/router, not Chrome. The unrelated Driver Management React text-test failure reported in the previous task was not changed; the full unrelated React suite was not rerun for this task.

Coverage includes the 20 requested scenarios through strict response tests, existing/expanded database lifecycle and concurrency tests, service contracts and widget checks: suitable/capacity cases; maintenance and confirmed conflicts; ignored pending requests; concurrent pending submissions; atomic Admin confirmation/409/alternative; strict progression gates; backend errors; fresh date/passenger/luggage/route checks; submission-time revalidation; shared assignment listing; driver mine endpoint and customer CONFIRMED status; midnight; idempotency. Automated coverage does not replace the remaining browser/device walkthrough.

## Files modified

Paths below are relative to `diladrive/`:

- `backend/diladrive-api/src/main/java/com/diladrive/service/CustomerTripService.java`
- `backend/diladrive-api/src/main/java/com/diladrive/controller/ApiExceptionHandler.java`
- `backend/diladrive-api/src/main/resources/application-local.properties`
- `backend/diladrive-api/src/test/java/com/diladrive/CustomerTripPostgresIT.java`
- `backend/diladrive-api/src/test/java/com/diladrive/StrictAvailabilityAuditTest.java`
- `mobile-app/lib/models/booking_draft.dart`
- `mobile-app/lib/services/booking_service.dart`
- `mobile-app/lib/widgets/booking_availability_message.dart`
- `mobile-app/lib/screens/customer/booking/booking_availability_page.dart`
- `mobile-app/lib/screens/customer/booking/booking_details_page.dart`
- `mobile-app/lib/screens/customer/booking/booking_fare_page.dart`
- `mobile-app/lib/screens/customer/booking/booking_review_page.dart`
- `mobile-app/lib/screens/customer/booking/booking_location_view.dart` (availability invalidation only; working location features preserved)
- `mobile-app/test/booking_backend_contract_test.dart`
- `mobile-app/test/strict_booking_availability_test.dart`
- `mobile-app/test/booking_review_retry_test.dart`
- `docs/strict-availability-final.md`

Builds also produce local target/dist outputs and tests use the existing ignored Flutter harness. No React Admin source change was needed; the existing shared picker and assignment APIs were retained and checked. Existing app_theme.dart, money_validation.dart, GPS/search, database relationships and unrelated modules were preserved.

## Remaining configuration and use

1. Enter **approved real company fare rates** in booking_fare_rate for the operating MPV/SEDAN types: base_fare, rate_per_km, applicable additional_charge and active. Matching is case-insensitive. None exist currently. Without them, pricing is intentionally blocked; this implementation does not claim live booking success.
2. Review/renew certificates for vehicles 3 and 17, and resolve vehicle 7's maintenance through normal operations before expecting them to be selectable. Review realistic luggage point capacities with occupied passenger seats; do not fill them using model-name guesses.
3. Review inconsistent legacy terminal assignment/booking statuses and missing actual end times from business records. No automatic history rewrite was performed.
4. Development routing/CORS settings remain as previously configured. Production still needs BOOKING_ROUTING_URL, company rates, database connectivity and exact allowed origins. No new provider/API key is required by this change.
5. Existing optional FCM requires BOOKING_PUSH_ENABLED, FIREBASE_PROJECT_ID, server credentials and registered device tokens. Persisted notifications do not depend on push delivery.
6. After backend restart, sign in again; hot-restart/relaunch Flutter to load the new structured response handling. Verify the intended API_BASE_URL (8080 or the explicitly chosen 8081 profile). The local launch script remains `./run-local.ps1 -UseWindowsTrust` in the backend folder.
7. Install the Windows C/C++ toolchain required by the existing PDF dependency to run the full native Flutter test suite. Perform the customer/Admin/driver walkthrough on Chrome/Android after actual company rates are configured.

Repeat backend tests with `RUN_CUSTOMER_TRIP_IT=true`, then `mvn '-Dtest=*Test,CustomerTripPostgresIT' '-Dnet.bytebuddy.experimental=true' test` (the Byte Buddy option is needed on this machine's Java 25). The opt-in audit is `RUN_STRICT_AVAILABILITY_AUDIT=true` with `-Dtest=StrictAvailabilityAuditTest`; it requires the previously verified geocoding request artifact and working Java HTTPS trust. Its connection is server-enforced read-only.
