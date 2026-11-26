# DilaDrive scheduled booking and assignment improvement

## Existing system and availability diagnosis

The React Admin app, Flutter customer and driver flows, Spring MVC services, and PostgreSQL already shared booking and assignment records. The customer flow posted Malaysia wall-clock dates, luggage quantities and geocoded endpoints to the live availability and pricing endpoints. Admin could preview and confirm a stored vehicle preference. Pending requests did not reserve either resource. A separate PostgreSQL hold table existed from an earlier workflow.

The availability evaluator collapsed passenger, luggage and scheduling failures into one message. It evaluated the vehicle without passing the linked driver into conflict detection, so it could quote a vehicle whose driver Admin could not assign. Admin required a literal driver availability value of `AVAILABLE`, even for a future slot with no conflict. The customer payload and the Spring DTO used the same passenger and bag-count fields and Malaysia wall-clock timestamps. There was no mandatory minimum passenger filter.

The local PostgreSQL audit on 25 November 2026 found five linked vehicles and no vans. Two operational Sedans had recorded passenger capacity of four, capped at three for scheduled bookings, and luggage capacity of two points each. A third Sedan was under maintenance. One MPV's road tax and service certification expired on 17 November 2026. The other MPV had valid vehicle documents and an active driver with the legacy `On Trip` flag. That flag requires a schedule check. Four passengers with three luggage points can only fit the second MPV in this fleet. A request exceeding those values may still have no eligible ride. The specific customer request that originally produced the message was unavailable for replay.

The local rate settings were active at RM 2 per kilometre. Category overrides were off. An empty category-rate table is valid under that configuration. Pricing still comes from the server.

## Updated workflow

1. The customer chooses locations on the existing map, then enters a pickup or airport landing slot, passengers, luggage and optional flight information and notes. The draft stays in memory across the booking stages.
2. **Choose your ride** shows the date, time, passenger and bag counts, then loadable categories in recommendation order with backend fares. Vehicle cards use category icons and omit model and pair identifiers. Availability failures offer date or trip edits or a retry for service errors.
3. On final submission Spring rechecks the session, trip, quote token, and category. It locks the linked resources, checks availability again and atomically saves the pending booking and temporary reservation. Browsing and quoting perform no booking or reservation writes.
4. Admin sees the suggested pair, validation checks and expiry in Bookings. **Change Assignment** validates and saves a replacement hold. **Verify Booking** validates the hold and pair and writes the confirmed assignment in the same transaction. **Reject Booking** records the Admin reason, releases the hold and notifies the customer.
5. The confirmed assignment appears in the existing Assignments and Driver Trips lists. My Bookings shows confirmed driver name, vehicle category, model, plate, fare and the current assignment milestone. Foreground push messages refresh the customer and driver lists.
6. Reassignment retains the booking ID and fare, records the reason, retains the previous assignment as `SUPERSEDED` and notifies the previous driver, replacement driver and customer. The old driver loses active access. The existing trip milestones, proof of service, corporate documents and payment routes continue to use the same booking.

Route failures still use the existing Admin distance-review path where available. Those requests cannot reserve a pair before route duration and fare are known.

## Recommendation and schedule rules

| Category | Recommended passengers | Maximum |
| --- | --- | --- |
| Sedan | 2–3 | 3 |
| MPV | 4–5 | 5 |
| 10 Seater Van | 6–8 | 8 |
| 14 Seater Van | 9–11 | 11 |
| 18 Seater Van | 12–15 | 15 |

The passenger maximum is the smaller of the configured seats and category limit. The recommendation starts at the smallest eligible category, with deterministic tie-breakers. The customer may select any larger suitable priced category. Final submission uses the selected category and resolves an eligible pair in driver-ID order. A concurrent submission may use another free pair in the same category. It receives a conflict if the category can no longer be reserved.

The internal luggage calculation still uses one, two and three points for small, medium and large bags. Configured independent backpack and large-bag limits still apply when present. Admin can maintain the combined capacity and optional limits in Vehicles or while registering a driver's linked vehicle. Missing type, passenger capacity or combined luggage capacity prevents use in scheduled recommendations.

Each occupied interval starts at the requested pickup or landing time and ends after the estimated road duration and configurable buffer. Times use Asia/Kuala_Lumpur with the schema's local timestamps. Intervals are half-open, so adjacent trips may touch. Both driver and vehicle are checked against confirmed assignments, valid holds and recorded unavailable periods. An in-progress trip that overruns its recorded end can still block the pair. Active accounts with legacy `BUSY` or `On Trip` flags are evaluated by the schedule. Inactive accounts, explicit driver unavailability, nonoperational vehicles and documents expired by pickup disqualify the pair. There is no live positioning or inter-trip route data. The configured buffer covers preparation and repositioning.

## Reservations and status

The existing `booking_vehicle_hold` has a single row per booking. Migration 031 adds the held driver's foreign key, a notification marker, and indexes for driver overlaps and expiry. Legacy vehicle-only rows are retained and excluded from current holds. Writes lock the booking when it exists and take PostgreSQL row locks in driver-then-vehicle order. This coordinates booking creation, edits, Admin reservation changes, confirmation and reassignment. The existing unique active-assignment index still protects the booking-to-assignment relationship.

| Event | Booking | Reservation | Assignment |
| --- | --- | --- | --- |
| Final request | Pending | Active until expiry or pickup, whichever is earlier | No driver trip |
| Admin changes suggested pair | Pending | Replaced atomically | No driver trip |
| Reservation expires | Pending | Expired and immediately excluded from availability | No driver trip |
| Admin confirms | Confirmed | Converted and hold row removed | Assigned |
| Customer cancels or Admin rejects a pending request | Cancelled or rejected | Released | No driver trip |
| Admin reassigns | Confirmed | Uses existing confirmed allocation transaction | Prior row superseded, replacement assigned |
| Driver progresses or completes | Confirmed during progress, completed at finish | No hold | Existing milestones and completed history |

Expired pending requests display the expired hold separately from the booking's verification status. The expiry worker sends one inbox notification per expired hold. Admin can re-reserve a valid pair before confirming. Repricing a pending booking through the existing explicitly accepted fare proposal releases the old-category hold. Manual legacy and distance-review requests may be directly confirmed after the existing Admin validation.

In-app notifications are persisted with each business transition. Firebase delivery remains asynchronous after commit. The Admin pickup monitor now checks future assigned trips for recorded resource problems and also flags missing assignments in the original two-hour window.

## Configurable policies

| Setting | Default | Purpose |
| --- | --- | --- |
| `BOOKING_BUFFER_MINUTES` | 30 | Existing travel and repositioning allowance |
| `BOOKING_RESERVATION_MINUTES` | 240 | Proposed four-hour Admin review window, limited by pickup time |
| `BOOKING_RESERVATION_SWEEP_MS` | 60000 | Expiry inbox notification interval. Availability checks expiry immediately |
| `BOOKING_REASSIGNMENT_CUTOFF_MINUTES` | 120 | Proposed normal cutoff. Authorized Admin can choose emergency reassignment before pickup and provide a reason |
| `BOOKING_ALLOW_REASSIGNMENT_UPGRADES` | false | Optional larger-category replacement when no same-category pair is eligible, with the original fare |

Confirm the four-hour hold, two-hour reassignment cutoff and optional upgrade policy with operations. Work that has started remains in the existing incident and trip workflows.

## API and user interface changes

- `POST /api/bookings/availability` returns category summaries without resource IDs. Failure statuses include `NO_VEHICLES`, `FLEET_NOT_CONFIGURED`, `PASSENGER_CAPACITY_UNAVAILABLE`, `LUGGAGE_CAPACITY_UNAVAILABLE`, `DRIVER_UNAVAILABLE`, `SCHEDULE_UNAVAILABLE`, and `SERVICE_ERROR`.
- `POST /api/bookings/estimate` still returns the signed server quote. `POST /api/bookings/customer` and its existing alias validate it, retain idempotency and return 409 for stale category availability. Booking edits re-evaluate the hold as part of the same transaction.
- `GET /api/bookings/{id}/assignment-preview` adds reservation state, expiry and the emergency-cutoff indicator. The Admin-only `PUT /api/bookings/{id}/suggested-assignment` replaces or renews a pair. Confirmation, rejection and reassignment reuse their existing routes.
- `GET /api/assignments/schedule` returns one driver's or vehicle's occupied intervals for the selected Malaysia date. Drivers and Vehicles show this status separately from account and operational state. Assignments gained pickup-date, driver and vehicle filters and displays schedule and booking identifiers from actual assignments.
- `GET /api/bookings/mine` includes the latest authorized confirmed assignment and any pending reservation status. `GET /api/assignments/mine` includes assigned vehicle category and model. Customer and Driver apps continue to use their existing authenticated views.

## Files

| Area | Files created or updated |
| --- | --- |
| Spring services | CustomerTripService, AssignmentServiceImpl, BookingService, BookingPriceService, BookingPickupCheck |
| Spring API and persistence | AssignmentController, BookingConfirmationController, AssignmentRequest, AssignmentResponse, CustomerBookingResponse, AssignmentRepository, AssignmentSummaryProjection, Vehicle, application.properties |
| PostgreSQL | New 031_pending_pair_reservations.sql, BASELINE_CURRENT.sql, schema README |
| React | Bookings.jsx, Assignments.jsx, Drivers.jsx, Vehicles.jsx, AssignmentResourcePicker.jsx, new AssignmentSchedule.jsx |
| Flutter customer | booking draft and customer booking models, booking detail, location, vehicle, availability and review screens, My Bookings, booking service and availability message |
| Flutter driver | Driver trip model, assigned trips and trip details |
| Tests | CustomerTripPostgresIT, PricingPostgresIT, StrictAvailabilityAuditTest, AssignmentServiceTest, Admin booking-assignment tests and Flutter booking contract, widget and availability tests |

## Existing use cases and design artifacts

| Use case | Description and activity change |
| --- | --- |
| Create Booking | Keep the existing location and trip steps. Add category selection, final-only reservation and stale-availability recovery. |
| View Booking | Show pending hold expiry and confirmed assignment details. Keep fare and document actions on the existing booking. |
| Verify Booking | Show and validate the suggested pair and confirm booking and assignment together. Include replacement, expiry and rejection branches. |
| Assign Driver & Vehicle | Trigger assignment from Verify Booking. Retain explicit repair for confirmed legacy bookings missing assignments. |
| Reassign Trip | Add cutoff, authorized emergency override, replacement validation, preserved fare and history. |
| View Vehicle | Display its day schedule alongside physical capacity, documents and operational status. |
| Update Vehicle | Keep the existing vehicle editor. Updated operational data feeds eligibility and the future-trip Admin monitor. |
| View assigned trip | Read confirmed allocations and updated vehicle details. Prior superseded allocations are excluded from active trips. |
| Update Trip Status | Keep the existing progression and authorization. Populate customer trip status from the current assignment. |

Update the System Sequence Diagram for category quotes, submission, Admin preview and atomic confirmation. In the Multi-Layer Sequence Diagram, show row locks and notifications persisted in the transaction, then Firebase after commit. In the Domain Class Diagram, retain the linked Driver and Vehicle, document the booking's optional pending reservation with both resource keys, and retain Assignment's self-reference for reassignment history.

## Run and verify

1. Use `BASELINE_CURRENT.sql` and the applicable documented schema steps for a new database. Apply `031_pending_pair_reservations.sql` to an existing database before restarting Spring. The local `DilaDrive` database already has migration 031. It retained 9 bookings, 5 vehicles, 3 assignments and 0 legacy holds.
2. From `backend/diladrive-api`, run `mvn spring-boot:run` with the existing local database and routing settings. Run `mvn test` for the regular suite. Enable `RUN_CUSTOMER_TRIP_IT=true` and include `CustomerTripPostgresIT` for the disposable PostgreSQL test schema. `PricingPostgresIT` uses real HTTP and the configured road provider when `RUN_PRICING_IT=true`.
3. From `web-admin`, run `npm run dev`, `npm run build` and `node --test tests/*.test.cjs`.
4. From `mobile-app`, run `flutter pub get`, `flutter analyze`, `flutter test` and `flutter build apk --debug` with the intended API settings. Native PDF tests require the Windows C/C++ toolchain.
5. For deployment acceptance, use a verified customer and configured fleet to submit a trip, confirm or change the pair in Admin, then verify the same booking and latest assignment on a driver and customer device.

## Verification and remaining checks

| Check | Result |
| --- | --- |
| PostgreSQL migration | Passed. A disposable upgrade schema retained its legacy hold on two consecutive applications. The migration then ran on the local database and preserved all audited row counts. |
| Maven regular and booking integration tests | Passed. 270 passed and 5 opt-in tests were skipped in that run. The run included 54 PostgreSQL booking cases for capacity, availability, concurrent holds, verification, expiry, reassignment, notification behavior and account access. |
| Final PostgreSQL and live API rerun | Passed. All 63 tests passed, including the 54 booking cases and 9 live pricing and fleet cases. |
| Live HTTP pricing and local fleet audit | Passed. All 9 tests passed. The real HTTP suite started Spring against an isolated schema and used the existing road provider. One priced trip returned 139.6706 km and RM 172.42 using its test category rate. The read-only production-shaped fleet query returned HTTP 200 and MPV for four passengers and three luggage points, then returned the luggage-capacity reason for an oversized request. The Windows trusted certificate store was needed for the live road provider on this host. |
| React | Passed. Production build and 36 tests. The project defines no separate lint command. |
| Flutter dependency resolution and analysis | Passed with the installed Flutter SDK. |
| Standard Windows Flutter test command | Blocked before tests by the existing PDFium native hook, which needs a configured Windows C/C++ toolchain. |
| Flutter regression tests in temporary copy | Passed. All 191 tests passed with only the Windows PDF native build hooks omitted in that copy. The project's dependencies, PDF code and native hooks were used for the Android build. |
| Android debug build | Passed. The APK is at `mobile-app/build/app/outputs/flutter-apk/app-debug.apk`. |

The test suite covers the transition from customer submission to Admin confirmation and driver/customer views with PostgreSQL, as well as expiry, the same-category replacement path, the emergency cutoff, interval overlap, duplicate confirmation, idempotency and both one-pair and two-pair concurrency. The existing simulated push-infrastructure failure coverage checks post-commit behavior. A deployed Firebase delivery and a physical-device workflow were outside the executed tests.

The existing scheduling engine has no route between consecutive trips, and the interface offers date and time editing without suggesting unverified alternative slots.
