# Assignment preview and confirmation fix

## Root cause

The Admin app and current source both use GET /api/bookings/{id}/assignment-preview. The old development JVM on port 8080 returned ENDPOINT_NOT_FOUND for this route while the older assignment-options route returned 401 without a session. The local database was also missing migrations 029 and 030, which the current backend needs.

Booking 9 starts at 16:00 Malaysia time and stores 832 seconds of driving duration. Adding 13 minutes 52 seconds and a 30-minute buffer gives 16:43:52. The previous summary rounded the drive to 14 minutes, which obscured the calculation.

## Changes

- BookingConfirmationController and AssignmentServiceImpl provide the booking reference, pickup time, requested vehicle, linked driver, exact drive duration and buffer, and separate preferred-pair checks for vehicle, driver, relationship, capacity and schedule. Preview and transactional confirmation share the validations. Existing saved buffers are preserved. A known duration with no saved end uses the configured buffer.
- Successful confirmation logs the Admin, booking, assignment, driver and vehicle IDs after the transaction returns. Existing audit timestamps, inbox notifications and optional after-commit push are retained.
- AssignmentResourcePicker, Bookings, Assignments and bookingAssignments.js display the exact schedule breakdown, show status-specific retry messages and keep submission unavailable on failed verification. Available preferred pairs are preselected. Legacy bookings without a saved vehicle ID show eligible alternatives.
- CustomerTripPostgresIT and the Admin booking assignment tests cover the endpoint and failure states.

## Verification

- Maven package succeeded with 71 tests, including 48 Spring/PostgreSQL integration cases in a disposable schema. Checks included authenticated preview, unauthenticated and unauthorized access, invalid and missing requests, exact schedule calculation, availability, conflict enforcement and persisted confirmation.
- The React production build and all 36 Admin tests passed.
- Migrations 029 and 030 were applied to the local DilaDrive database. Booking 9 was verified to remain PENDING with no historical preferred vehicle ID.
- The local backend was restarted using the tested artifact. The actual route on port 8080 returned 401 without a session, confirming it is registered and protected. Authenticated confirmation was verified on disposable PostgreSQL data.
