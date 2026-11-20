# Confirm & Assign 404 investigation ? 17 November 2026

## Confirmed cause

The frontend uses POST http://localhost:8080/api/bookings/7/confirm-and-assign. Axios defaults to http://localhost:8080/api (no frontend environment override was present) and includes session credentials. The request body is driverId (number), vehicleId (the selected driver's linked vehicle ID), scheduledEndAt (local ISO date/time), and tripFare (number).

BookingConfirmationController already declares @RequestMapping("/api/bookings") and @PostMapping("/{id}/confirm-and-assign"). The application scans com.diladrive, including this controller. No context path is configured in the checked-in application configuration. The frontend URL and HTTP method are correct; no replacement/fallback confirmation API was introduced.

Before restart, a real unauthenticated POST with a valid-shaped body returned Spring's generic 404 JSON (timestamp/status/error/path, no application message), while GET /api/bookings returned the application's 401 message. This establishes that the confirmation request was not reaching the controller's admin-session check. PostgreSQL also confirmed booking 7 exists, remains PENDING, and has zero assignments. Thus this observed 404 was not Booking not found, Driver not found or Linked vehicle not found.

Port 8080 belonged to Java PID 17056, started at 06:50:21. Its Maven parent pointed to this exact backend/diladrive-api project. The new controller class was compiled at 07:12:33. The already-running Spring context had not registered the newly introduced controller. This was a stale backend process, not a wrong request path or missing database booking.

The stale process was stopped. A replacement of the same DilaDrive project is now running on port 8080 (PID 2096, started 07:28:46, launched by the IDE Maven installation). It was left running. An additional launch attempt detected the occupied port and exited; it did not replace the working server.

Verified against the replacement server:

- POST /api/bookings/7/confirm-and-assign without a session: 401 with message Please sign in as an admin. This confirms the controller is now registered and authentication remains required.
- OPTIONS on that path: 200, Allow: POST,OPTIONS.
- An intentionally nonexistent API action: 404 with code ENDPOINT_NOT_FOUND and the method/path in its message.

Do not interpret the unauthenticated 401 as a failed route. Log in again after restarting: the old in-memory session is no longer valid. No real booking was assigned merely for testing.

## Code changes in this fix

- web-admin/src/pages/Bookings.jsx: show the confirmation-specific error formatter in the modal.
- web-admin/src/services/bookingAssignments.js: preserve application resource/validation/conflict messages; recognize generic legacy 404 and explicit ENDPOINT_NOT_FOUND; show the actual POST URL and backend restart/base-URL/context-path guidance. No fallback request is sent.
- backend/diladrive-api/src/main/java/com/diladrive/controller/ApiExceptionHandler.java: distinguish missing endpoint responses with ENDPOINT_NOT_FOUND, log endpoint method/path, and log application rejection status/reason for resource lookup failures. No credentials, session cookies or request bodies are logged by these new diagnostics.
- web-admin/tests/booking-assignments.test.cjs: test generic route 404, resource 404, explicit endpoint error and ordinary backend messages.
- backend/diladrive-api/src/test/java/com/diladrive/BookingConfirmationPostgresIT.java: run the real embedded HTTP server as well as existing MockMvc checks; add real Admin login/cookie, confirmation, refreshed assignment list, retry, missing route, missing booking and missing driver checks.
- docs/confirm-and-assign-404.md: this report.
- backend/diladrive-api/README.md: link the restart/troubleshooting instructions.

The existing transactional confirmation/assignment service was retained, including account/availability, driver?vehicle relationship, capacity/expiry, overlap validation, pessimistic locking and duplicate prevention. No migration, favicon, customer verification or booking business-record changes were made by this fix.

## Checks executed

- React production build: PASS (npm.cmd --prefix diladrive/web-admin run build).
- Related frontend tests: 10 PASS (booking-assignments.test.cjs and assignment-pair.test.cjs).
- Backend compilation and targeted suite: 53 PASS, zero failures/errors/skips. BookingConfirmationPostgresIT: 13; AssignmentServiceTest: 15; BookingControllerTest: 7; CustomerBookingCreationTest: 17; CustomerBookingQueryTest: 1.
- The two new real HTTP tests use an actual local Tomcat random port, BCrypt test-admin login and a cookie session, then POST confirmation and a fresh GET /api/assignments. The expected persisted ASSIGNED row was returned, the booking was CONFIRMED, and retry returned 409 without a duplicate.
- Unmapped endpoint returns ENDPOINT_NOT_FOUND; mapped requests for nonexistent booking/driver return their specific resource messages. Existing transaction rollback, concurrent overlap and duplicate-request tests also pass.
- All write tests used a new disposable PostgreSQL schema (baseline + existing 020/021/022), with current_schema asserted before every test. The schema was removed after testing. No application records were reset or deleted.

Not executed: interactive browser submission with the user's Admin credentials, actual confirmation of production/local booking 7, or Flutter device testing. The live port-8080 routing/authentication probes are distinct from the authenticated end-to-end HTTP tests on the isolated test server. The full unrelated frontend suite was not rerun in this fix.

## Restart command

The updated backend is currently running; another restart is not needed for this fix. For future changes that add controllers, stop the existing Spring Boot run terminal/task first, then run from the repository root:

~~~powershell
mvn -f .\diladrive\backend\diladrive-api\pom.xml spring-boot:run
~~~

If Maven is not on PATH, the installation verified on this machine is:

~~~powershell
& "$env:APPDATA\Code\User\globalStorage\pleiades.java-extension-pack-jdk\maven\latest\bin\mvn.cmd" -f .\diladrive\backend\diladrive-api\pom.xml spring-boot:run
~~~

Use Java 17+ and the existing PostgreSQL settings. Keep only one backend on 8080. A separate launch using a different JDK initially encountered missing offline plugin dependencies and a Java PKIX trust failure. Using Windows' trusted certificate store allowed secure dependency resolution without disabling TLS verification; the currently working IDE backend uses its existing Java/Maven setup. No permanent certificate or system-environment setting was changed.
