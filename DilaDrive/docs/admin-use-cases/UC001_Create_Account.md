# UC001 — Create Account (Admin)

## Finalized business rules

- **UC001** covers Admin creating a driver account and SUPER_ADMIN creating an administrator account. The existing role restriction for creating administrators stays in force. Admin Web cannot create customer accounts.
- **Customer registration** is a separate Flutter workflow for Individual (`NORMAL`) and Corporate (`COMPANY`) customers. The server must set every newly registered customer to `PENDING`.
- **UC004 — Verify Account** is a separate Admin use case. Admin can approve or reject a pending registration. Approval allows the customer to create bookings. Pending and rejected customers are ineligible.
- **Customer Create Booking** checks approval on the Spring Boot backend. This document represents approval using the existing `ACTIVE` status. It uses `REJECTED` for the proposed explicit rejection state.
- The Admin customer and Admin booking creation routes were retired after reviewing all callers. The account and booking flows below distinguish code findings from test results.

## Implementation after Option A

| Requirement | Current Implementation | Expected Implementation | Files to Change | Status |
|---|---|---|---|---|
| 1. Admin creates driver accounts | Drivers page posts driver details and a required vehicle. The server creates an `ACTIVE` driver and links the new vehicle in one transaction. | Keep admin driver creation. | None for confirmed rule. | YES |
| 2. Only SUPER_ADMIN creates administrator accounts | The UI gates Add Admin by role. The API requires an active `SUPER_ADMIN`. | Preserve both checks. | None for confirmed rule. | YES |
| 3. Admin cannot create customer accounts | Add customer and the creation-only form fields are removed. `POST /api/customers` has no handler and returns 405. | Remove this creation path from the UI and make the POST unavailable on the backend. Preserve customer list, profile editing and verification. | `Customers.jsx`, `CustomerController.java`, `CustomerService.java` (implemented). | YES |
| 4. Individual self-registration | Flutter sends `NORMAL` to the mobile registration endpoint. | Keep Individual self-registration. | None for confirmed rule. | YES |
| 5. Corporate self-registration | Flutter sends `COMPANY` and company details to the same endpoint. The server requires both company fields. | Keep Corporate self-registration. | None for confirmed rule. | YES |
| 6. New customers always start pending | The mobile registration service always assigns `PENDING`. Its request DTO has no account status field. The Admin POST is retired. | Keep server-assigned `PENDING` and close the Admin POST. | Route removal as in rule 3. | YES |
| 7. Admin approves or rejects registrations | Verify sends `ACTIVE` via a generic customer update. There is no explicit rejection action or pending-only decision validation. The list labels every non-`ACTIVE` status Pending. | Explicitly support approval and rejection, enforce valid transitions on the server, and display Pending, Verified and Rejected accurately. | `Customers.jsx`, `CustomerController.java`, `CustomerService.java`. | PARTIAL |
| 8. Only approved customers create bookings | Customer submissions require `ACTIVE`. The Admin POST is retired. The reviewed web client had no Create Booking action. | Only active customers can submit new bookings. | `BookingController.java`, `BookingService.java` (implemented). | YES |
| 9. Booking eligibility is enforced by Spring Boot | Both current customer create paths check the persisted customer status before any booking write. The older internal adapter also checks it. | Keep the backend check on every supported customer create path. | No fix identified for the current customer routes. The former Admin route is retired. | YES for customer routes |
| 10. New account passwords are securely hashed | All supported account creation paths use BCrypt. `PasswordService.matches()` also supports legacy plain-text values, and successful customer authentication upgrades such a value. | Preserve BCrypt for new accounts. Track legacy credential cleanup separately. | None for new-account hashing. | YES for new accounts |

## Flow A — Admin creates a driver account

1. **Admin Web.** `App.jsx › renderPage()` opens Drivers. In `Drivers.jsx`, **Create Driver Account** opens `Modal` with `DriverFormFields`, including the vehicle fields.
2. **Submit.** `Drivers.jsx › handleCreateDriver()` uses `driverForm.js › validateDriverFields()` and `toDriverCreatePayload()`, adds the password, then sends `POST /api/drivers` through the Axios instance in `api.js`.
3. **Backend and database.** `DriverController.createDriver()` applies `@Valid` to `DriverCreateRequest` and nested `VehicleCreateRequest`, and checks the session through `AdminSession.requireAdminId()`. `DriverService.createDriver()` checks and normalizes input, hashes the password, sets `ACTIVE`, and calls `DriverRepository.save()` for `driver`. `VehicleService.createVehicleForDriver()` checks the plate and driver link and calls `VehicleRepository.save()` for `vehicle`. `vehicle.driver_id` links the records within the same transaction.
4. **Result.** The controller returns **201** and the saved driver, whose password is write-only in JSON. `handleCreateDriver()` closes the modal, displays success and calls `fetchDrivers()`, which uses `driverManagement.js › loadDriverManagement()`. A refresh failure has a separate message after the save.
5. **Alternatives and errors.** Closing before submission creates no records. UI errors stop submission and highlight fields. The controller or `ApiExceptionHandler` returns **400** for invalid input and **401** for a missing admin session. Duplicate email detected by the driver service currently returns **400**. Duplicate plate or a caught integrity error returns **409**. Unchecked vehicle-save failure is expected to roll back the driver transaction. Actual rollback was not tested.

**Expected flow.** Keep this UC001 branch and its existing session authorization. The confirmed rules do not change the current vehicle requirement.

## Flow B — Super Admin creates an administrator account

1. **Admin Web.** `App.jsx › renderPage()` passes the current user to `Admins.jsx`. The **Add Admin** action opens `AdminFormModal` only when the current role is `SUPER_ADMIN`.
2. **Submit.** `AdminFormModal.handleSubmit()` checks confirmation and prepares the payload. An optional image is read by `handleImageChange()`. `Admins.jsx › saveAdmin()` sends `POST /api/admins` through `api.js`.
3. **Backend and database.** `AdminController.createAdmin()` validates `AdminCreateRequest` and gets the session ID. `AdminManagementService.createAdmin()` calls `requireSuperAdmin()` and `requireActor()` to check the current administrator record and role, checks input and email availability, hashes the password, sets `ACTIVE` and calls `AdminRepository.save()` for `admin`. `AdminResponse.from()` omits the password.
4. **Result and alternatives.** The controller returns **201**. `saveAdmin()` closes the form and calls `loadAdmins()`. The refreshed row is the visible confirmation. Closing the form before submitting saves nothing. If the API role is omitted, the service defaults it to `ADMIN`.
5. **Errors.** Invalid input yields **400**. A missing session or missing/inactive actor yields **401**. An active admin without `SUPER_ADMIN` receives **403**. Duplicate email returns **409**. `AdminFormModal.handleSubmit()` uses `getErrorMessage()` to show the failure. `loadAdmins()` reports list-refresh errors separately.

**Expected flow.** Preserve the existing UI and API authorization and UC001 branch.

## Flow C — Individual customer self-registration

1. **Flutter.** The customer registration action in `login.dart › _LoginPageState.build()` opens `register.dart › RegisterPage`. The customer selects **Individual Customer**, enters their details and submits. `_RegisterPageState._register()` validates the form and supplies `NORMAL` with null company fields.
2. **Submit.** `auth_service.dart › AuthService.registerCustomer()` trims profile inputs and posts JSON to `POST /api/auth/mobile/register` using the API base URL from `app_config.dart`. Empty phone becomes null. It sends no status and no confirmation field.
3. **Backend and database.** `AuthController.mobileRegister()` validates `MobileRegistrationRequest`, then calls `MobileAuthService.registerCustomer()`. The service checks the email using `CustomerRepository.findByCustEmailIgnoreCase()`, creates a `Customer` with type `NORMAL`, forces `PENDING`, uses `PasswordService.encode()` and calls `CustomerRepository.saveAndFlush()` for `customer`. Company details are stored as null.
4. **Result.** The API returns **201** with a text confirmation. `_register()` shows a verification message and returns to login. Registration does not create a session. The current login path, `AuthController.mobileLogin() → MobileAuthService.login() → authenticateCustomer()`, can authenticate a customer before approval. Booking submission has its own status check.
5. **Errors.** Invalid fields stop the Flutter submission or yield **400** server-side. Duplicate email yields **409**. The service maps caught registration integrity failures to the same email conflict message. `AuthService._serverErrorMessage()` reads text or JSON messages. Timeouts and connection failures become `AuthException`, shown by `_register()`.

**Expected flow.** Keep self-registration through Flutter and automatic `PENDING`. Booking eligibility begins after UC004 approval.

## Flow D — Corporate customer self-registration

1. **Flutter.** In `register.dart › RegisterPage`, select **Corporate Customer**. The form displays Company Name and Company Registration Number. `_register()` validates both and calls `AuthService.registerCustomer()` with `COMPANY` and those fields.
2. **Submit and persist.** `auth_service.dart › registerCustomer()` sends the same **POST**. `AuthController.mobileRegister()` validates the DTO. `MobileAuthService.registerCustomer()` additionally requires both trimmed company fields. It stores them in the same `customer` row, sets type `COMPANY` and status `PENDING`, hashes via `PasswordService.encode()`, and saves through `CustomerRepository.saveAndFlush()`.
3. **Result and errors.** The API returns **201**, and `_register()` shows the same message and returns to login. If either company field is missing, the form blocks submission and a request reaching the service is rejected with **400**. Duplicate email and connection failures follow the Individual flow.

**Expected flow.** Keep the corporate branch and UC004 approval requirement. Both categories use the same booking status gate.

## Flow E — Admin verifies a customer account

1. **Admin Web.** `Customers.jsx › fetchCustomers()` calls `GET /api/customers`. `CustomerController.getAllCustomers() → CustomerService.getAllCustomers() → CustomerRepository.findAll()` reads `customer`. The UI maps exactly `ACTIVE` to **Verified** and all other values to **Pending**.
2. **Approve.** Admin selects **Verify** and confirms the dialog opened by `askToVerify()`. `handleVerify()` sends `PUT /api/customers/{id}` with identity fields and `custStatus: "ACTIVE"`.
3. **Backend and database.** `CustomerController.updateCustomer()` checks the admin session and delegates to transactional `CustomerService.updateCustomer()`. It loads the customer with `CustomerRepository.findById()`, applies the supplied status, and calls `CustomerRepository.save()`. There is no dedicated verification endpoint or transition check in this implementation.
4. **Result and errors.** **200** returns the updated customer and `handleVerify()` updates local state and closes the dialog. Cancelling the dialog sends no request. Missing session returns **401**, missing customer **404**. Other API errors appear in the dialog. **Reject has no implemented UI or dedicated decision flow.** The generic update accepts a supplied status, which alone does not enforce a rejection workflow.

**Expected flow for UC004.** Present Approve and Reject for pending registrations. Persist the decision through a backend operation that verifies the actor and accepts only a valid transition. Return the committed status so the UI shows the correct label. Keep ordinary profile updates separate from decision authority.

## Flow F — Customer booking eligibility

1. **Flutter.** The signed-in customer confirms the draft in `booking_review_page.dart › _BookingReviewPageState._confirmBooking()`. The normal path needs a fresh recommendation and fare. The alternate distance-review path uses `booking_service.dart › requestRoadDistanceReview()`.
2. **Submit.** `booking_service.dart › createBooking()` or `requestRoadDistanceReview()` calls `_submitBooking()`. For a new booking it sends a POST to `/api/bookings/customer/complete` or `/api/bookings/customer/road-distance-review` with `MobileSession.headers` and an `Idempotency-Key`.
3. **Backend gate.** `BookingController.complete()` also serves `POST /api/bookings/customer`. Both that handler and `requestDistanceReview()` call `requireCustomerId()` to read the customer role and ID from the server session. `CustomerTripService.create()` or `requestDistanceReview()` enters the common private `create()` method. It uses `CustomerRepository.findById()` and returns **403** for any customer whose persisted status is other than `ACTIVE`, before writing the booking or retry record.
4. **Persist and return.** If approved and all other checks pass, `CustomerTripService.create()` saves a `Booking` through `BookingRepository.saveAndFlush()` and records the submission through `BookingRequestIdempotencyRepository`. The new booking's status is `PENDING` for booking confirmation. The customer's account stays `ACTIVE`. The API returns **201** with a booking reference. `_submitBooking()` reads it and the review page displays it.
5. **Alternatives and errors.** Missing session yields **401**, wrong role **403**, missing customer **404**, and a non-approved customer **403**. Approved customers still need name, phone, valid trip data and the relevant availability or distance-review checks. Retrying the same accepted submission key and payload returns its existing reference. Reusing it for changed trip details returns **409**. `booking_service.dart › _request()` clears the session on **401** and the review page sends the user to login. Other errors display their message, including account verification errors.
6. **Other creation code.** `BookingController.createCustomerBooking()` is an internal compatibility method with no HTTP mapping. Its `BookingService.createCustomerBooking()` also checks `ACTIVE`. The former Admin POST mapping and its dedicated save method have been removed. Admin booking views and verification still use their existing handlers.

**Expected flow for Customer Create Booking.** Preserve the live database status check for both customer routes. The final Option A rule allows only customer booking creation.

## API endpoints and status transitions

| Endpoint | Current purpose and success response |
|---|---|
| `POST /api/drivers` | Admin creates driver and vehicle. **201**, driver `ACTIVE`. |
| `POST /api/admins` | Active SUPER_ADMIN creates administrator. **201**, administrator `ACTIVE`. |
| `POST /api/customers` | Retired. **405 Method Not Allowed**. |
| `POST /api/auth/mobile/register` | Individual or Corporate self-registration. **201**, always `PENDING`. |
| `POST /api/auth/mobile/login` | Creates the mobile session on valid credentials. **200**, existing account status. |
| `GET /api/customers` | Admin lists registrations. **200**, customer list. |
| `PUT /api/customers/{id}` | Generic admin update used for approval. **200**, updated customer. |
| `POST /api/bookings/customer`, `POST /api/bookings/customer/complete`, `POST /api/bookings/customer/road-distance-review` | Customer creation. **201**, booking `PENDING`. Non-active customer receives **403**. |
| `POST /api/bookings` | Retired. **405 Method Not Allowed**. |

| Customer transition | Current | Required |
|---|---|---|
| New → `PENDING` | Forced by mobile registration. | Flutter registration only, with server-assigned pending status. |
| `PENDING` → `ACTIVE` | Verify uses generic PUT without a pending-only guard. | Explicit approval by Admin. |
| `PENDING` → `REJECTED` | Generic status write exists. No explicit reject action or distinct UI label. | Explicit rejection by Admin, with the customer ineligible to create bookings. |
| `ACTIVE` or `REJECTED` → another state | Generic PUT has no transition guard. | Reopening, reversing or resubmitting a decision requires policy confirmation. |

## Validation rules

| Path | Current validation |
|---|---|
| Driver and linked vehicle | Driver DTO requires name (120), email (160), phone (30) and password (minimum 8). Service checks email uniqueness and the BCrypt 72 UTF-8 byte limit. React checks phone digits, loaded-list duplicates and password confirmation. Nested vehicle requires model, plate, type, passenger capacity ≥ 1, luggage capacity ≥ 0 and status. Service checks duplicate plate and existing driver link. Expiry fields are optional in the create DTO. |
| Administrator | DTO requires name (120), email (254), phone (30), address (500), password and confirmation. Service validates role, matching passwords, minimum 8 characters, at most 72 UTF-8 bytes, email uniqueness and optional PNG/JPEG/WebP data up to 2 MB. |
| Mobile registration | Flutter checks name, email format, minimum 8 characters, 72 UTF-8 bytes and matching confirmation. DTO limits name to 120, email to 160 and optional phone to 30. Type must be `NORMAL` or `COMPANY`. Password confirmation is UI-only. The backend normalizes fields, checks duplicate email and requires company name (150) and registration number (30) for Corporate. |
| Verification and booking | Generic customer update checks an admin session and customer existence. There is no verification transition validation. Current customer create handlers verify the customer session and load persisted status. An approved account must still pass the booking data and availability checks. |

## Include / extend audit

No include or extend relationship identified for UC001.

The supplied use case image shows **Create Account** and **Verify Account** with direct actor associations. The existing `diladrive/docs/DilaDrive-Use-Cases-and-CRUD.drawio` Admin tab has separate account creation entries and no include/extend attached to them. Its current “Create customer or driver account” label conflicts with the finalized scope. Document the customer self-registration separately. **UC004 depends on a previously registered customer being available for review.** It occurs after registration and is initiated by Admin. This is a business precondition, with no UML include or extend inferred.

## Source file inventory

Paths below are relative to the workspace root. The flow steps above trace UI, API, service and persistence. The baseline defines the account tables, booking foreign key and retry table. The driver-vehicle uniqueness index is supplied by migration 020.

| Layer | Exact file path | Function / Class / Method | Purpose |
|---|---|---|---|
| React navigation | `diladrive/web-admin/src/App.jsx` | `App`, `renderPage` | Selects the three pages and passes the current user to Admins. |
| React page and inline form | `diladrive/web-admin/src/pages/Customers.jsx` | `Customers`, `openCustomerForm`, `changeCustomerField`, `saveCustomer`, `fetchCustomers` | Opens and submits edits for existing customers and refreshes the list. Standalone form component: **MISSING**. |
| React page and components | `diladrive/web-admin/src/pages/Drivers.jsx` | `Drivers`, `Modal`, `DriverFormFields`, `handleCreateDriver`, `fetchDrivers` | Collects driver and vehicle details, submits the request and shows the result. |
| React page and component | `diladrive/web-admin/src/pages/Admins.jsx` | `Admins`, `AdminFormModal`, `handleSubmit`, `handleImageChange`, `saveAdmin`, `loadAdmins`, `getErrorMessage` | Gates administrator creation by role, collects details and shows the result. |
| Frontend validation | `diladrive/web-admin/src/services/driverForm.js` | `validateDriverFields`, `toDriverCreatePayload`, `toDriverPayload` | Checks driver and vehicle inputs and builds the POST body. |
| API service | `diladrive/web-admin/src/services/api.js` | `api`, response interceptor | Configures the base URL, credentials and request error propagation. Account POST calls are in the pages. |
| API error helper | `diladrive/web-admin/src/services/apiErrors.js` | `getApiErrorMessage`, `logApiError` | Formats driver request errors and logs selected request metadata. |
| Spring controller | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/controller/CustomerController.java` | `getAllCustomers`, `updateCustomer`, `requireAdminId` | Lists or updates existing customers, and serves the mobile profile. |
| Spring controller | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/controller/DriverController.java` | `createDriver`, `requireAdminId` | Handles driver and vehicle creation. |
| Spring controller | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/controller/AdminController.java` | `createAdmin`, `requireAdminId` | Handles administrator creation. |
| Spring error handler | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/controller/ApiExceptionHandler.java` | `handleValidation`, `handleResponseStatus`, `handleDataIntegrity`, `handleDatabaseUnavailable` | Converts exceptions into HTTP responses. |
| Session helper | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/security/AdminSession.java` | `requireAdminId` | Validates the session ID for the driver controller. Customer and admin controllers use their own helpers. |
| Spring service | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/CustomerService.java` | `updateCustomer`, `applyCompanyDetails`, `encodePassword` | Validates corporate profile updates and saves existing customers. |
| Spring service | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/DriverService.java` | `createDriver`, `validateProfile`, `ensureEmailIsAvailable`, `normalizeAvailability`, `encodePassword`, `toVehicle` | Creates an active driver and starts the shared transaction. |
| Spring service | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/VehicleService.java` | `createVehicleForDriver`, `normalizeStatus` | Validates and saves the linked vehicle within the existing transaction. |
| Spring service | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/AdminManagementService.java` | `createAdmin`, `requireSuperAdmin`, `requireActor`, `validateProfileImage` | Checks authorization and input and saves the administrator. |
| Request model | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/dto/DriverCreateRequest.java` | `DriverCreateRequest` | Validates driver fields and requires a nested vehicle. |
| Request model | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/dto/VehicleCreateRequest.java` | `VehicleCreateRequest` | Validates nested vehicle fields. |
| Request model | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/dto/AdminCreateRequest.java` | `AdminCreateRequest` | Validates the administrator create body. Mobile registration uses its own DTO. |
| Response model | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/dto/AdminResponse.java` | `AdminResponse.from` | Maps administrator data without the password. |
| Repository | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/repository/CustomerRepository.java` | `CustomerRepository.save` (inherited) | Persists the customer. |
| Repository | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/repository/DriverRepository.java` | `DriverRepository.save` (inherited), `findByDriverEmailIgnoreCase` | Checks the email and persists the driver. |
| Repository | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/repository/VehicleRepository.java` | `VehicleRepository.save` (inherited), `findByDriverId`, `existsByVehiclePlatenoIgnoreCase` | Checks the link and plate and persists the vehicle. |
| Repository | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/repository/AdminRepository.java` | `AdminRepository.save` and `findById` (inherited), `findByEmailIgnoreCase` | Loads the actor, checks the email and saves the administrator. |
| Entity | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/entity/Customer.java` | `Customer` | Maps to `customer`. Password is write-only in JSON. |
| Entity | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/entity/Driver.java` | `Driver` | Maps to `driver`. Password is write-only in JSON. |
| Entity | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/entity/Vehicle.java` | `Vehicle` | Maps to `vehicle` and stores the driver ID. |
| Entity | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/entity/Admin.java` | `Admin` | Maps to `admin`. |
| Database tables | `diladrive/database/schema/BASELINE_CURRENT.sql` | `customer`, `driver`, `vehicle`, `admin` | Defines the tables, primary keys, vehicle foreign key and normalized account email indexes. |
| Database indexes | `diladrive/database/schema/020_one_driver_one_vehicle.sql` | `uq_vehicle_current_driver`, `uq_vehicle_plate_normalized` | Enforces the unique driver link and normalized plate once applied. |
| Flutter entry | `diladrive/mobile-app/lib/screens/auth/login.dart` | `_LoginPageState.build` | Opens customer registration. |
| Flutter form | `diladrive/mobile-app/lib/screens/auth/register.dart` | `RegisterPage, _register, _required, _field` | Collects the two types and validates the registration form. |
| Flutter auth API | `diladrive/mobile-app/lib/services/auth_service.dart` | `AuthService.registerCustomer, _serverErrorMessage` | Submits registration and handles API errors. |
| Flutter configuration | `diladrive/mobile-app/lib/config/app_config.dart` | `AppConfig.apiBaseUrl` | Provides API base URL. |
| Flutter session | `diladrive/mobile-app/lib/services/mobile_session.dart` | `MobileSession.headers, clear` | Supplies booking credentials and clears an expired session. |
| Flutter booking | `diladrive/mobile-app/lib/screens/customer/booking/booking_review_page.dart` | `_confirmBooking` | Submits the reviewed draft and displays the result. |
| Flutter booking API | `diladrive/mobile-app/lib/services/booking_service.dart` | `createBooking, requestRoadDistanceReview, _submitBooking, _request` | Sends the customer submission and handles server errors. |
| Driver list loader | `diladrive/web-admin/src/services/driverManagement.js` | `loadDriverManagement` | Refreshes the driver management data. |
| Spring controller | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/controller/AuthController.java` | `mobileRegister, mobileLogin` | Handles registration and establishes mobile sessions on login. |
| Spring controller | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/controller/BookingController.java` | `complete, requestDistanceReview, requireCustomerId, verifyBooking` | Routes customer creation and admin verification. |
| Spring service | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/MobileAuthService.java` | `registerCustomer, login, authenticateCustomer` | Creates the pending customer and authenticates existing accounts. |
| Spring service | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/PasswordService.java` | `encode, matches` | Hashes mobile registration passwords and supports legacy login matching. |
| Spring service | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/CustomerTripService.java` | `create, requestDistanceReview` | Checks live eligibility, saves booking and stores retry metadata. |
| Spring service | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/BookingService.java` | `createCustomerBooking, verifyBooking` | Handles admin verification and the older internal customer path. |
| Request model | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/dto/MobileRegistrationRequest.java` | `MobileRegistrationRequest` | Validates mobile account type and field limits. |
| Request model | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/dto/TripQuoteRequest.java` | `TripQuoteRequest` | Carries customer booking submission data. |
| Repository | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/repository/BookingRepository.java` | `save, saveAndFlush` | Persists bookings. |
| Repository | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/repository/BookingRequestIdempotencyRepository.java` | `insertIfAbsent, findForUpdate, save` | Stores retry and booking references. |
| Entity | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/entity/Booking.java` | `Booking` | Maps to booking, with customer ID. |
| Entity | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/entity/BookingRequestIdempotency.java` | `BookingRequestIdempotency` | Maps to booking_request_idempotency. |

## Ownership and recommended fixes

| Owner | Recommended change |
|---|---|
| UC001 — Create Account (Admin) | Implemented the UI and API retirement. Customer list, edit and verification remain available. The existing UML labels still require alignment to Driver Account and Admin Account. |
| Customer Registration | Preserve both Flutter registration categories and `MobileAuthService` as the owner of the initial `PENDING` status. Keep tests for both categories and duplicate email. |
| UC004 — Verify Account | Add explicit approve/reject decisions, require an allowed transition, use distinct status labels, and prevent generic profile edits from deciding eligibility. Select error responses and audit fields when the workflow is implemented. |
| Customer Create Booking | Keep the existing `ACTIVE` checks and cover `PENDING`, `REJECTED` and `ACTIVE` on both customer submission paths. Option A retired the admin-only POST and its dedicated service method. |

## Questions requiring confirmation

1. Should a rejected customer be able to sign in and see the decision? The current customer login allows valid credentials regardless of status.
2. Can a rejected registration be reopened or resubmitted, and is a rejection reason or notification required?

## Verification results

| Check | Result |
|---|---|
| React production build | Passed using `npm run build` with a temporary output directory. Portal actions were checked in source. Interactive browser testing was not performed. |
| Spring Boot Maven suite | 221 tests reported, 216 passed and 5 skipped, with zero failures or errors. Includes driver creation, SUPER_ADMIN authorization, mobile registration and the two retired POST routes. |
| PostgreSQL customer-trip suite | 29 passed in an isolated schema. Covers active customer creation and HTTP 403 for both pending and rejected customers on all three supported POST routes. |
| Flutter static analysis | `flutter analyze --no-pub` passed with no issues. Device tests were not run. |

See [UC001 Implementation Report](UC001_Implementation_Report.md) for the file-by-file changes and booking impact.
