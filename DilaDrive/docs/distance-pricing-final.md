# Final distance-based pricing — 18 November 2026

## Root cause and inspected system

Read-only inspection confirmed the normal database is `DilaDrive`, schema `public`.
The existing `booking_fare_rate` table contained **zero rows**. The error was not
caused by inactive SEDAN pricing, a different pricing table or a category mismatch.
The old estimate service required exactly one active rate and raised HTTP 503 when
none existed. Availability called that same priced quote, incorrectly making
missing pricing look like a failure to check physical vehicle availability.

Vehicle categories are strings in `vehicle.vehicle_type`, not a separate category
entity. The live fleet has three Sedan vehicles and two MPV vehicles, with mixed
capitalization. Configuration groups category identifiers case-insensitively and
trims whitespace without rewriting vehicle records. The existing pricing table is
reused; no second pricing configuration table or example company rates were added.

There was no Settings page in the checked-in Admin shell. Settings → Pricing now
uses that same shell, theme and authenticated Axios client. Session authentication
and controller role checks are preserved; no security protections were disabled.

## Implemented behavior

- Admin can list, retrieve, create, update and activate/deactivate current category
  pricing. Categories come from PostgreSQL. The UI displays base fare, RM/km,
  existing additional charges, status and updated timestamp, with validation,
  loading/empty states, feedback and deactivation confirmation.
- `POST /api/bookings/availability` performs physical capacity, operational status,
  certification, maintenance and confirmed-assignment interval checks independently
  of pricing. Recommendation ties use capacity and vehicle ID, rather than price.
- `POST /api/bookings/estimate` uses real OSRM driving distance and active category
  pricing. Calculation is server-side BigDecimal: base + road km × rate/km, plus
  any already configured additional charge shown separately. Final MYR fare uses
  two decimals and HALF_UP. No new additional charges were invented.
- Missing/inactive pricing returns **422 `PRICING_NOT_CONFIGURED`**; invalid rates
  return `INVALID_PRICING_CONFIGURATION`. Ordinary missing business configuration
  is no longer reported as service-unavailable 503.
- Flutter Step 3 consumes unpriced availability. Step 4 consumes the server fare;
  a pricing error preserves valid availability/selections and allows retry, while
  preventing continuation/submission. The existing five-step screen flow remains.
- Fare previews carry a signed, 15-minute quote token. Submission revalidates the
  verified owner, trip, capacity/schedule, selected category and current pricing.
  Changed/expired/unreviewed quotes return **409 `FARE_REVIEW_REQUIRED`** with a
  refreshed quote. Flutter explicitly asks the customer to review/accept it before
  another submission. Pricing rows are share-locked while saving the accepted quote.
- Creation remains idempotent and saves **PENDING**, with no assigned vehicle,
  driver or vehicle hold. The existing `fare_snapshot` now stores JSON including
  rates, charges, road distance, MYR total, pricing ID and configuration timestamp.
  Pricing updates do not rewrite previous accepted snapshots or fares.
- Same-category assignment preserves the accepted fare, even after rate updates.
  Changing category recalculates its proposed fare from the saved road distance.
  A changed amount requires a recorded proposal and explicit owner acceptance
  before confirmation/reassignment. Identical-amount category changes preserve
  the amount and record history without a new monetary acceptance requirement.
  Existing resource checks, row locks and duplicate-assignment constraints remain.
- Admin can send/review proposals in the shared assignment picker; customers review
  and accept them in Booking Details. Original fare/snapshot and proposal/acceptance
  history are preserved. Repricing is blocked once invoiced or payment-recorded.
- Quotations for snapshot bookings use the accepted amount. Invoices retain stored
  document/accepted booking amounts and reject a stale purchase-order amount after
  an accepted price revision. Existing document amounts are never updated by rate
  changes. Receipts/manual payment handling retain the recorded-payment behavior.
- Nonnegative pricing, including an explicitly configured zero total, is supported
  through booking, confirmation, quotation and invoice. Legacy manual quotations
  retain their positive-amount rule.
- The unused Flutter hardcoded straight-line fare calculator now fails explicitly;
  it cannot provide a client-side price/distance fallback. Active booking screens
  use the backend. The legacy entry-point signature is retained for compatibility.

## Database and deployment configuration

Applied `database/schema/025_distance_pricing.sql` to the existing live database.
It extends `booking_fare_rate` with an identity pricing ID and created/updated
timestamps, adds normalized uniqueness and fleet-category validation, and creates
`booking_fare_revision` for price history. The original rates table and booking
snapshots are retained. No company prices, bookings, assignments or users were
seeded, deleted or reset. All test fixtures were confined to isolated schemas and
those schemas were removed after tests.

The document amount constraint permits zero-valued quotations, purchase orders and
invoices for legitimately configured zero fares; payments and receipts still require
positive recorded amounts. Existing positive-valued documents are unchanged.

For another existing database, apply migrations 023, 024 and then 025 before using
this code. This project still uses explicit SQL migrations, not automatic Flyway.

Normal development uses the existing default `development` profile and OSRM URL.
Production must supply its existing OSRM-compatible `BOOKING_ROUTING_URL` and exact
frontend CORS origins. No provider keys or prices were added to Flutter/React.

Set `BOOKING_QUOTE_SIGNING_KEY` to the same private random value, at least 32 bytes,
on every production replica. Do not commit it. Development generates an in-memory
key if unset; a restart then requires customers to refresh/review pending previews.
Persisted accepted booking amounts remain valid after restart.

**Company pricing is still unconfigured.** The live public pricing table was
rechecked after testing and still has zero rows. Admin must enter the real approved
base fare and RM/km rate, then activate the category in Settings → Pricing. Rates
and existing test Admin/customer credentials were requested for final public-schema
verification; none have been supplied. Do not copy the isolated test rates below
into company configuration.

## API and test evidence

`PricingPostgresIT` starts a real HTTP Spring Boot application connected to an
isolated PostgreSQL schema, authenticates through real Admin/customer/driver login,
persists rates through the real secured pricing endpoints and calls the real OSRM
provider. It does not mock authentication, pricing, the database, or road routing.
Its temporary test users/rates never enter `public`.

Representative actual HTTP estimate response from those tests (test rates only):

```json
{
  "available": true,
  "availabilityStatus": "AVAILABLE",
  "estimatedDistanceKm": 139.6706,
  "durationSeconds": 6829,
  "baseFare": 17.35,
  "ratePerKm": 1.2345,
  "distanceCharge": 172.42,
  "totalFare": 189.77,
  "currency": "MYR"
}
```

The UiTM Kampus Jasin → KLIA Terminal 1 Main Building response includes 1,469
geometry points. Test prices above are fixtures, not approved company pricing.
The complete real response is saved in
`backend/diladrive-api/target/pricing-real-api-estimate.json`.

Real HTTP cases cover 201 pricing creation, 200 updates/status changes, 409 duplicate
configuration, 400 invalid/unknown rates/categories, rejected customer/driver pricing
access, 200 AVAILABLE with no fare when pricing is absent/inactive, 422 missing
pricing, 409 refreshed-fare review, 201 idempotent PENDING submission, 409 unavailable
submission, 200 transactional assignment, duplicate-assignment rejection, customer
proposal acceptance/history and correct quotation/invoice amounts after rate updates.

Backend verification log: `backend/diladrive-api/target/pricing-final-tests.log`.
Final result: **246 discovered, 241 passed, 5 skipped, zero failures/errors**.
The last real pricing HTTP server listened on port 53768 in a disposable schema;
this is a test-assigned port, not application configuration.
The full suite includes `CustomerTripPostgresIT` (28 strict-availability/booking
tests), new real-provider pricing integration tests and existing document/manual
payment/session tests. Optional unrelated live audits remain skipped.

React `npm.cmd run build` passed. Flutter analyze passed with no issues. The actual
booking-service/availability/legacy-calculator source passed **22 Flutter tests**
in a temporary harness whose lib junction points to the app source. Its reduced
dependencies avoid the unrelated `pdfium_dart` Windows C++ hook that prevents the
normal full app test runner from starting. App dependencies were not changed.

An additional, non-required React Node regression run had **27 passes and one
failure** in the pre-existing Driver form test `editing without a vehicle renders
a valid profile form` (`tests/driver-forms.test.cjs`). Driver form code was not
modified by this task; the required production build is successful.

The normal API runs on **8080** with the tested code and existing database. Its
pricing endpoint correctly returns 401 without Admin authentication; the Admin
origin preflight returns 200 with credentialed CORS. Runtime log is
`backend/diladrive-api/target/pricing-api.log`.

Full browser/Android booking walkthrough and successful company-priced estimates
on public-schema 8080 remain **unverified pending real company rates and an existing
test session**. The isolated real HTTP workflow is verified; this report does not
claim a completed Flutter/browser end-to-end test or configured live company rates.

## Source files changed

Backend main sources (under `backend/diladrive-api/src/main/`):

- `resources/application.properties`
- `java/com/diladrive/dto/TripQuoteRequest.java`, `QuotationRequest.java`
- `java/com/diladrive/entity/Booking.java`
- `java/com/diladrive/controller/PricingController.java` (new), `BookingPriceController.java` (new), `BookingController.java`, `ApiExceptionHandler.java`
- `java/com/diladrive/service/PricingService.java` (new), `PricingException.java` (new), `QuoteTokenService.java` (new), `BookingPriceService.java` (new)
- `java/com/diladrive/service/CustomerTripService.java`, `AssignmentServiceImpl.java`, `BookingService.java`, `DocumentServiceImpl.java`

Database: `database/schema/025_distance_pricing.sql` (new), `database/schema/README.md`.

React: `web-admin/src/pages/Settings.jsx` (new), `App.jsx`, `pages/Bookings.jsx`,
`components/AssignmentResourcePicker.jsx`, `components/FareProposalPanel.jsx` (new).

Flutter: `mobile-app/lib/models/booking_draft.dart`, `services/booking_service.dart`,
`services/booking_fare_calculator.dart`, `screens/customer/booking/booking_fare_page.dart`,
`screens/customer/booking/booking_review_page.dart`, `screens/customer/booking_details.dart`,
`widgets/booking_fare_proposals.dart` (new).

Tests: new backend `PricingServiceTest`, `PricingControllerTest`, `PricingPostgresIT`;
updated `CustomerTripPostgresIT`; standalone configuration isolation in
`LoginCorsHttpTest`, `LiveJasinRoutingTest`, `RoutingProfileTest`; Flutter
`booking_service_test.dart`, `strict_booking_availability_test.dart`,
`booking_fare_calculator_test.dart`. This report is `docs/distance-pricing-final.md`.

No clean/package/replacement project build was run. Maven compiled changed backend
sources for tests; React's requested production build was run, and the existing API
runtime classpath was reused for restarting 8080.
