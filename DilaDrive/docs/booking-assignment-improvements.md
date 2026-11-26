# DilaDrive booking confirmation and reassignment

## Customer preference

Availability and fare responses identify the eligible vehicle represented by each category. Flutter submits that ID with the category and accepted quote. The server validates both against current eligibility and saves the preference separately from the assigned vehicle and driver. A new request remains PENDING. The customer quote exposes the vehicle reference, model and capacity but does not expose the associated driver.

## Confirm & Assign

The Admin-only assignment-preview endpoint retrieves the saved vehicle and its linked driver. It uses the same driver status, vehicle status, passenger and luggage limits, credentials, time windows and overlap rules as confirmation. Complete bookings use their saved driving duration and positioning buffer to determine the earliest valid end. Older bookings without that estimate still accept an explicit scheduled end.

A valid preferred pair is preselected in the React modal. If the preference fails, the modal shows the reason and lets Admin choose an eligible alternative pair. The backend locks the booking and resources, verifies the pair again, and saves the confirmed booking, active assignment and inbox notifications in the same transaction. The response contains the persisted booking status, fare and assignment details. Existing push delivery runs after commit when configured.

## Reassignment and accepted fares

Reassignment uses the existing driver-vehicle relationship and supports active trips independent of how far away pickup is. The preview excludes the current allocation from scheduling conflicts and prioritises the same vehicle when it is eligible with another linked driver. The old assignment becomes SUPERSEDED and the replacement points to its ID. The replacement stores the reason and Malaysia-local assignment time. In-app notifications reach the former driver, replacement driver and customer. Existing cancellation notifications are retained.

Same-category assignments retain the accepted fare. A different category for a quoted booking requires explicit customer acceptance through the existing fare proposal workflow, including when the calculated amount is unchanged. A submitted override of the accepted fare is also rejected before assignment. Manual legacy bookings retain their existing fare handling.

## Pre-pickup checks

Every five minutes the Spring scheduler checks confirmed trips due within two hours in Asia/Kuala_Lumpur. Missing assignments, inactive or unavailable resources, invalid pairs, capacity issues and schedule conflicts generate an Admin inbox alert. The booking and pickup timestamp form a persistent deduplication key. The check leaves driver and vehicle changes to Admin. The defaults can be configured through BOOKING_PICKUP_CHECK_ENABLED and BOOKING_PICKUP_CHECK_INTERVAL_MS. Recorded driver and vehicle unavailable periods are included. The project has no separate incident feed to inspect automatically.

## Deployment and files

Apply 030_booking_assignment_preference.sql after 029 before starting the updated backend on an existing database. The current baseline includes the same fields for clean installations. Older bookings keep their existing records and may need an explicit alternative because their exact preference was never recorded.

| Area | Modified files |
| --- | --- |
| Flutter | booking_service.dart, booking_review_page.dart, booking_backend_contract_test.dart, booking_service_test.dart |
| React Admin | Bookings.jsx, Assignments.jsx, AssignmentResourcePicker.jsx, FareProposalPanel.jsx, bookingAssignments.js, booking-assignments.test.cjs and assignment-pair.test.cjs |
| Spring | CustomerTripService, AssignmentServiceImpl, BookingPriceService, NotificationService, BookingPickupCheck, DiladriveApplication, BookingConfirmationController and AssignmentController, booking and assignment DTOs, entities and assignment repository projections |
| PostgreSQL | 030_booking_assignment_preference.sql, BASELINE_CURRENT.sql, schema README |
| Backend tests | CustomerTripPostgresIT.java |

## Verification

- The React production build succeeded and all 34 Admin tests passed.
- All 91 selected Maven tests passed. The 47-case CustomerTripPostgresIT suite ran against a disposable PostgreSQL schema and covered the exact preference, missing or unavailable resources, conflicts, booking confirmation, reassignment history, fare acceptance, notifications and pickup alert deduplication.
- Migration 030 was applied twice in a separate disposable upgrade schema. The existing booking and assignment rows were preserved.
- Flutter analysis passed with no issues. All 103 booking-related Flutter tests passed in the temporary test copy with the existing Windows PDFium build hooks omitted. The submission tests verify that the requested vehicle ID is sent as a preference and that no assigned vehicle or driver is submitted.

## Remaining operational checks

The development database was not migrated during this change. Test a complete live Customer-to-Admin booking on the target deployment after the migration. Verify actual Firebase delivery with a configured device if push delivery is enabled. Pre-pickup alerts indicate recorded operational conflicts and cannot guarantee arrival.
