# UC001 implementation report · Option A

Admin can create drivers and an active SUPER_ADMIN can create administrators. Customers register through Flutter. Only active customers can submit new bookings. Both retired Admin POST routes return HTTP 405 because the POST mappings were removed and the existing paths still support reads.

## Files changed

Paths are relative to the workspace root.

| File Path | Function / Method | Change Made | Reason | Test Result |
|---|---|---|---|---|
| `diladrive/web-admin/src/pages/Customers.jsx` | `openCustomerForm`, `saveCustomer`, form JSX | Removed Add customer, registration-only controls and the create request. The form edits an existing customer with PUT. | Customer registration is mobile-only. | React build passed. UI reviewed in source. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/controller/CustomerController.java` | `createCustomer` (removed) | Removed the root POST mapping. | Prevent direct admin account creation for customers. | MockMvc returned 405. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/CustomerService.java` | `createCustomer` (removed) | Removed the helper after checking production and test callers. | The helper only supported the retired endpoint in production. | Existing customer update cases passed. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/controller/BookingController.java` | `createBooking` (removed) | Removed the root Admin POST mapping. | Every new booking must enter through a customer route. | MockMvc returned 405. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/BookingService.java` | `createBooking(Booking)` (removed) | Removed only the legacy admin creation helper. | The retired controller was its only caller. | Maven and PostgreSQL suites passed. |
| `diladrive/backend/diladrive-api/src/test/java/com/diladrive/controller/CustomerOnlyCreationTest.java` | `adminCannotCreateCustomersOrBookingsUsingRetiredEndpoints` | Added MockMvc assertions for both retired routes and verified zero service calls. | Cover direct requests with an admin session. | Passed. |
| `diladrive/backend/diladrive-api/src/test/java/com/diladrive/service/CustomerServiceTest.java` | Corporate update tests | Updated fixtures to represent registered accounts and retained allowed/invalid corporate edit coverage. | The retired create helper is no longer callable. | Passed. |
| `diladrive/backend/diladrive-api/src/test/java/com/diladrive/CustomerTripPostgresIT.java` | `pendingAndRejectedCustomersCannotCreateThroughAnyCustomerRoute` | Exercises three HTTP routes with two denied statuses and verifies there are no booking rows. | Verify eligibility enforcement before persistence. | Passed against PostgreSQL. |
| `diladrive/docs/admin-use-cases/UC001_Create_Account.md` | UC001 documentation | Updated flows, API status, comparison and results. | Record the implemented rules and the remaining UC004 decisions. | Reviewed against source and test results. |
| `diladrive/docs/admin-use-cases/UC001_Implementation_Report.md` | Implementation report | Added file and impact summaries. | Record the approved scope and verification. | Reviewed against the diff. |

## Admin Create Booking Removal – Impact Analysis

- Retired **POST /api/bookings** by removing its handler and unshared service helper. A direct POST receives HTTP 405.
- Reviewed `web-admin/src/pages/Bookings.jsx`, navigation and React API calls. There was no Create Booking action or navigation item calling this endpoint. No booking UI changes were required.
- **View Booking** still uses `GET /api/bookings`. **Verify Booking** still uses existing verification and confirm-and-assign handlers. The assignment workflow, booking rejection and existing fare and document operations retain their handlers. The removal does not change the customer editing workflow.
- **POST /api/bookings/customer**, **POST /api/bookings/customer/complete**, and **POST /api/bookings/customer/road-distance-review** remain available. They still use customer session and role checks, a persisted `ACTIVE` check, and the existing trip validation, availability, fare and idempotency logic.
- Booking, customer and vehicle data were preserved. There were no schema changes or migrations. The PostgreSQL suite used a disposable schema.

## Preserved account workflows

- `POST /api/drivers` still creates an active driver and linked vehicle together. Existing validation and duplicate email/plate handling were kept.
- `POST /api/admins` still checks the actor is an active SUPER_ADMIN. Both administrator roles and BCrypt hashing were kept.
- `POST /api/auth/mobile/register` still accepts Individual and Corporate registrations. The registration request has no status field and the service always sets `PENDING` before saving.
- Customer listing, details, profile edits and the existing Verify action remain available. UC004 still needs an explicit reject action, correct rejected status presentation and server-side transition checks.

## Test results and limits

| Check | Result |
|---|---|
| `npm run build` with temporary Vite output directory | Passed. Customers, Drivers, Admins and Bookings compiled. UI actions were reviewed in source. Interactive browser testing was not performed. |
| `mvn -o -B test` | Passed. 221 reported, 216 passed and 5 skipped. Zero failures or errors. |
| `mvn -o -B -Dtest=CustomerTripPostgresIT test` with `RUN_CUSTOMER_TRIP_IT=true` | Passed. 29 tests, zero failures or errors. Verified active creation and HTTP 403 for pending and rejected accounts. |
| `flutter analyze --no-pub` | Passed with no issues. Device registration and booking were not exercised. |
