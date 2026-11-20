# Booking confirmation and assignment

## Root cause and local audit (17 November 2026)

The old Bookings form called PUT /bookings/{id}/verify (and sometimes a separate fare write). That operation only confirmed the booking. Assignment creation lived on a different page/API. Booking Details displayed booking.driverId/vehicleId but never loaded driver_assignment, so those labels could disagree with the actual trip status and Assignments list.

Read-only PostgreSQL audit found booking IDs 3, 4, 5 and 7 PENDING with resource pointers but no assignment, and booking 6 CONFIRMED with pointers but no assignment. No duplicate active assignments were found. These business records were left unchanged. A pointer alone is now reported as a legacy inconsistency, never as a saved assignment.

## Final flow

Customer submission remains Pending. In Bookings, Admin selects an ACTIVE/AVAILABLE driver, sees the real linked vehicle as a provisional selection, supplies a planned trip end and final fare where needed, then clicks Confirm & Assign.

POST /api/bookings/{id}/confirm-and-assign accepts driverId, optional vehicleId (validated against the actual linked vehicle), scheduledEndAt and optional tripFare. Booking/driver/vehicle locks, account/availability/type/capacity/expiry checks, time conflict checks, fare, booking CONFIRMED, assignment ASSIGNED and database notifications run in one transaction. A failure rolls everything back. Repeated or simultaneous requests for the same booking get a conflict instead of creating another active assignment. Driver-level locking also serializes competing overlapping bookings for the same pair.

There is no inferred duration. Explicit scheduled_end_at was already introduced by migration 020. Adjacent time ranges are allowed. An active legacy reservation without an end is conservatively treated as continuing indefinitely from its pickup; unknown pickup schedules block reuse until resolved.

The standalone /verify endpoint cannot newly confirm a Pending booking; it returns an instruction to use Confirm & Assign. Existing confirmed bookings are returned without a new mutation. The legacy POST /assignments API remains compatible for confirmed bookings, using the same validation and locks, while its separate frontend creation workflow is removed.

Confirmed bookings without an active assignment offer Complete missing assignment as an explicit repair. Pending legacy rows still require Admin confirmation; nothing auto-confirms or auto-creates assignments.

Assignments retains view, search/filter, trip status, completion/cancellation and reassignment. View Bookings replaces Create Assignment. Reassign changes the existing active assignment and booking pointers together and rejects terminal assignments. Existing history semantics remain unchanged. /assignments/mine reads the same database rows, scoped to the authenticated driver; other drivers cannot advance those trips.

Bookings derives driver/vehicle/trip status from actual assignment records, retaining terminal history for display. Both pages reload on navigation/focus and after successful mutations through a shared browser event. Request-version guards prevent stale Booking fetches from replacing newer results. Confirmation has an immediate submission guard and disabled/loading button. Success only follows a successful API response; refresh errors are displayed separately.

Only existing database notification infrastructure was found in the backend. Notifications commit with the transaction and are invisible after rollback; no new push provider or paid integration was introduced.

## Migration

022_active_booking_assignment.sql is a manual forward migration after 020/021. It checks for duplicate active assignments, aborts for manual review if necessary, and creates uq_assignment_active_booking. It does not alter business rows. Applied successfully to the local DilaDrive database and to the isolated test schema. Apply the same reviewed script to other environments before deployment; there is no automatic migration runner. No additional provider/API key is required.

## Files changed in this task

- web-admin/src/App.jsx
- web-admin/src/pages/Bookings.jsx
- web-admin/src/pages/Assignments.jsx
- web-admin/src/services/bookingAssignments.js
- web-admin/tests/booking-assignments.test.cjs
- web-admin/tests/assignment-pair.test.cjs
- backend/diladrive-api/src/main/java/com/diladrive/controller/BookingConfirmationController.java
- backend/diladrive-api/src/main/java/com/diladrive/dto/BookingConfirmationRequest.java
- backend/diladrive-api/src/main/java/com/diladrive/service/AssignmentServiceImpl.java
- backend/diladrive-api/src/main/java/com/diladrive/service/BookingService.java
- backend/diladrive-api/src/main/java/com/diladrive/repository/AssignmentRepository.java
- backend/diladrive-api/src/test/java/com/diladrive/BookingConfirmationPostgresIT.java
- backend/diladrive-api/src/test/java/com/diladrive/service/AssignmentServiceTest.java
- backend/diladrive-api/src/test/java/com/diladrive/service/CustomerBookingCreationTest.java
- database/schema/022_active_booking_assignment.sql
- database/schema/README.md
- docs/booking-confirm-and-assign.md

## Checks actually executed

- npm.cmd --prefix diladrive/web-admin run build: PASS.
- node --test on booking-assignments.test.cjs and assignment-pair.test.cjs: 7 PASS.
- Full frontend Node suite: 23 PASS, 1 FAIL (24 total). The existing driver-forms.test.cjs test editing without a vehicle renders a valid profile form expects the text legacy driver has no registered vehicle, which the current Drivers form does not render. Drivers.jsx was not changed in this task; this failure was observed on the initial suite run as well.
- Maven offline compilation and targeted tests: 51 PASS, no failures/errors/skips. Classes: BookingConfirmationPostgresIT (11), AssignmentServiceTest (15), BookingControllerTest (7), CustomerBookingCreationTest (17), CustomerBookingQueryTest (1).
- PostgreSQL integration tests used a separately provisioned disposable schema (baseline + 020 + 021 + 022), never the application tables. The disposable schema was removed after verification. Verified HTTP confirm, persisted fresh reads, list/details/driver ownership, conflict rollback including notifications, duplicate retry and concurrent requests, concurrent overlapping bookings, explicit legacy repair, reassign, completed/cancelled rejection, adjacent periods, unknown legacy end, wrong linked vehicle, disabled driver, insufficient capacity, required end time, authentication/validation, database-insert failure rollback and unique-index enforcement.

The integration test is opt-in: set BOOKING_ASSIGNMENT_TEST_SCHEMA to a newly provisioned diladrive_booking_check_<digits> schema and DATABASE_URL to that schema via currentSchema. The test asserts current_schema() before each test and commits fixtures only in that disposable schema. Never point it at the application schema.

Not executed: interactive browser clicks/browser refresh, Android/Flutter end-to-end, and device push delivery. Persistence and authorization were verified via Spring MockMvc and PostgreSQL, and UI render/mapping via Node tests. The legacy application records still need explicit Admin review/repair. Restart/deploy the updated API and frontend together before using the new action.
