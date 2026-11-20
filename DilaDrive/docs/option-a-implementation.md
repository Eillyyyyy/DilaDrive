# Option A implementation report

Implemented 17 November 2026. The latest one-driver/one-vehicle rule supersedes the earlier optional-vehicle and read-only Vehicle Management requests.

## Root cause and data audit

The Edit handler previously threw an error when `GET /api/drivers/{id}` returned a null vehicle. That is a real legacy-data condition, not an HTTP cancellation failure. The existing mapping used an unconstrained scalar `vehicle.driver_id`; its foreign key did not prevent multiple vehicles per driver. A read-only audit of the local PostgreSQL database found drivers **3 and 4** without vehicles. No orphan vehicles or duplicate current links were found.

The application database contained 5 drivers, 3 vehicles and 2 assignments before and after applying the migration. No business rows were updated or deleted. The two legacy accounts remain unchanged. A later audit found no unsupported driver/vehicle status values and no active legacy assignments with missing end times.

## Implemented behavior

- Admin-only creation requires both sections. Driver and vehicle are created in one transaction; driver status is ACTIVE, passwords are BCrypt hashes, and normalized email/plate uniqueness is enforced. Creation DTO logging redacts the password.
- Driver profile edits preserve the current relationship. Updating the existing vehicle is explicit. A legacy missing relationship displays a registration action instead of throwing a global error. Completing registration through the edit form creates the missing vehicle and saves the profile in the same transaction. It cannot replace an existing linked vehicle.
- Vehicles has separate View and Edit actions. Edit updates the existing ID and cannot change its linked driver. Vehicles with active booking assignments cannot be edited; this conservative safeguard requires those reservations to be completed/cancelled or reassigned first.
- Vehicle operational states are AVAILABLE, MAINTENANCE and INACTIVE. Legacy IN_USE/UNAVAILABLE inputs are interpreted conservatively as INACTIVE. Existing stored values are not rewritten automatically.
- Assignments select a driver and automatically derive their linked vehicle. A mismatched explicit vehicle ID is rejected. Account status, manual availability, operational status, capacities, expiry dates, duplicate booking assignment and time overlap are checked under row locks.
- Admin enters a scheduled end date/time after pickup. Reservation intervals are half-open: an end at 10:00 permits another pickup at 10:00. Future reservations no longer globally mark drivers/vehicles unavailable. Completing a reservation does not overwrite administrator-managed availability or operational status.
- Active legacy reservations without end times conservatively block reuse from their known pickup onward until completed/cancelled or explicitly reassigned with an end time. An unknown pickup blocks reuse entirely. Historical completed/cancelled assignments are retained.
- Existing Flutter response fields remain present; scheduledEndAt is additive. Customer verification was not changed. No Flutter device test was executed.
- Strict Mode lifecycle requests now have cancellation cleanup. Expected ERR_CANCELED failures are not logged as HTTP errors. No browser trace was available to attribute previously observed cancellations to a particular initiator.

## Database

`database/schema/020_one_driver_one_vehicle.sql` was tested in an isolated schema and **applied to the local DilaDrive database**. It adds:

- A non-null vehicle driver foreign-key value and unique current driver index (the existing FK remains in place).
- Normalized plate and driver-email unique indexes.
- Deferred triggers enforcing paired creation and preventing removal of a current vehicle while its driver remains.
- Nullable `driver_assignment.scheduled_end_at`, preserving old history.

Legacy drivers without vehicles are grandfathered; no fabricated vehicles or bulk data corrections are performed. The migration stops on orphan vehicles, duplicate links or normalized identifier duplicates. The superseded, unapplied 019 data-rewrite script was removed. Other environments must apply 020 before running the updated API. See `database/schema/README.md`.

## Files changed for the driver/vehicle work

Paths below are relative to `diladrive/`. Existing unrelated IDE and Flutter workspace edits were preserved.

Backend production, under `backend/diladrive-api/src/main/java/com/diladrive/`:

- `controller/DriverController.java`
- `dto/DriverCreateRequest.java`, `dto/DriverUpdateRequest.java`, `dto/AssignmentRequest.java`, `dto/AssignmentResponse.java`
- `entity/Driver.java`, `entity/Vehicle.java`, `entity/Assignment.java`
- `repository/AssignmentRepository.java`, `repository/AssignmentSummaryProjection.java`
- `service/DriverService.java`, `service/VehicleService.java`, `service/AssignmentServiceImpl.java`
- `service/MobileAuthService.java` retains the driver ACTIVE-only login check added during the preceding investigation.

Frontend:

- `web-admin/src/pages/Drivers.jsx`, `Vehicles.jsx`, `Assignments.jsx`
- `web-admin/src/services/driverForm.js`
- `web-admin/src/services/driverManagement.js`, `apiErrors.js` include the request-lifecycle handling from the preceding investigation.

Database/documentation:

- `database/schema/020_one_driver_one_vehicle.sql`, `database/schema/README.md`
- `docs/option-a-implementation.md`

Tests:

- `backend/diladrive-api/src/test/java/com/diladrive/OptionAPostgresIT.java`
- `backend/diladrive-api/src/test/java/com/diladrive/DriverVehicleReadOnlyPostgresIT.java`
- `backend/diladrive-api/src/test/java/com/diladrive/controller/DriverApiSessionTest.java`, `DriverControllerTest.java`
- `backend/diladrive-api/src/test/java/com/diladrive/service/DriverServiceTest.java`, `VehicleServiceTest.java`, `AssignmentServiceTest.java`
- `backend/diladrive-api/src/test/java/com/diladrive/service/MobileAuthServiceTest.java` includes the ACTIVE/DISABLED regression coverage from the preceding investigation.
- `web-admin/tests/driver-forms.test.cjs`, `driver-lifecycle.test.cjs`, `vehicle-readonly.test.cjs`, `assignment-pair.test.cjs`

Additional driver-related files retained from the preceding integration work (already changed before Option A):

- Backend: `controller/ApiExceptionHandler.java`, `controller/AuthController.java`, `controller/VehicleController.java`, `dto/DriverDetailsResponse.java`, `repository/VehicleRepository.java`, `security/AdminSession.java` under the same Java source root.
- Frontend: `web-admin/src/services/api.js`, `web-admin/tests/driver-api.test.cjs`.

## Tests actually executed

All final checks passed:

- React production build: `npm.cmd --prefix diladrive/web-admin run build`.
- Frontend tests: `node --test diladrive/web-admin/tests/*.test.cjs` � **17 passed**. Includes create/edit forms, legacy no-vehicle editing, explicit registration payloads, vehicle edit rendering, linked-pair assignment forms, end-time fields and request cancellation diagnostics.
- Spring Boot unit/MVC suite � **159 passed**.
- `OptionAPostgresIT` in an isolated PostgreSQL schema � **10 passed**. Includes paired creation, hashing, ACTIVE/DISABLED login, vehicle GET/PUT persistence after clearing the persistence context, duplicate-plate transaction rollback, driver/vehicle edit, explicit legacy registration, database uniqueness/deferred constraints, adjacent bookings, overlap rejection, active-assignment vehicle-edit protection and linked-pair reassignment.
- `DriverVehicleReadOnlyPostgresIT` against the actual application database � **1 passed**. Driver, vehicle and assignment lists returned HTTP 200; both legacy detail requests succeeded with null vehicles; row counts were unchanged.
- Follow-up `DriverServiceTest` after password-log redaction � **11 passed** (a recheck of tests already included in the 159).
- Spring compilation and `git diff --check` passed.

Maven used the installed Java 25 runtime with `-Dnet.bytebuddy.experimental=true -DforkCount=0 -DskipTests=false`. The isolated integration test requires `OPTION_A_TEST_SCHEMA` and a matching datasource currentSchema pointing at a disposable schema initialized from BASELINE_CURRENT.sql plus 020 (with a test-only legacy row inserted before 020). It refuses to run against the normal application schema. The read-only test uses the configured application database and does not mutate rows.

## Remaining actions and limits

- Admin must complete vehicle registration for drivers **3 and 4** using real vehicle information. They cannot be assigned bookings beforehand.
- Other deployed databases need the migration, and running backend/frontend processes need to load the new code.
- No interactive browser session, Flutter device test or multi-process concurrency stress test was executed. Persistence and API behavior were tested through Spring MVC and PostgreSQL; concurrency protection uses pessimistic driver/vehicle row locks and database uniqueness constraints.
