# Customer and Driver hardening report

## Schema reconciliation

Read-only \d public.customer confirmed that dev already has cust_companyname VARCHAR(150), cust_companyaddress TEXT, cust_contactperson VARCHAR(100), and company_registration_number VARCHAR(30). Customer.java and BASELINE_CURRENT.sql lacked the address and contact-person columns. Both were updated to match the existing columns. No migration or live schema change was made.

Corporate registration writes contactPerson to cust_contactperson. The live cust_name column is NOT NULL, so corporate records use the company name there as the account display name. New and edited bookings use the corporate contact person when present, with the previous customer name retained as a fallback for older records. Assignment.java uses driver_assignment as the source of truth for allocation and progress.

## Use cases and verification

DONE below means that the listed backend behavior passed the Maven test suite. Full mobile runtime, deployed configuration, and PostgreSQL integration tests are marked separately.

| Use case | Endpoint | Validation added | Authorization rule | Tests | Status |
| --- | --- | --- | --- | --- | --- |
| Registration | POST /api/auth/mobile/register | Required phone, normalized email, strong password, matching confirmation, corporate fields and input limits | Public customer registration; any new account is PENDING | MobileAuthServiceTest and MobileValidationAndPaginationTest | DONE |
| Login and session | POST /api/auth/mobile/login; shared Admin login | Normalized account key, bounded attempts, generic credential failures, fresh session on success | Public login; authenticated mobile session gets only its assigned role | AuthControllerMobileSessionTest and LoginCorsHttpTest | DONE |
| Existing profile views and updates | GET/PUT /api/customers/me; GET/PUT /api/drivers/me; POST /api/drivers/me/password | Existing profile validation | Session identity and role | Existing controller/service tests pass; full profile field audit UNVERIFIED | PARTIAL |
| Booking creation | POST /api/bookings/customer/complete and /customer/road-distance-review | Verified customer required before routing, idempotency or persistence | PENDING and REJECTED receive 403 | CustomerBookingHardeningTest MockMvc exercises both states and both paths | DONE |
| Booking list and detail | GET /api/bookings/mine and /mine/{id} | Bounded response pages; detail scoped in repository | Customer session; cross-customer detail returns 404 | CustomerBookingHardeningTest and MobileValidationAndPaginationTest | DONE |
| Customer booking update and cancel | PUT /api/bookings/{id}/customer/complete; legacy PUT /api/bookings/{id}/customer; POST /api/bookings/{id}/cancel | Booking action table; illegal actions return 409; existing active-assignment restrictions retained | Customer owns locked booking; complete edit rechecks availability for a pending booking | CustomerBookingHardeningTest and existing booking tests; live race and confirmed rescheduling UNVERIFIED | DONE for supported pending edits and cancellations |
| Timeline and commercial documents | GET /api/bookings/{id}/timeline; GET /api/documents/customer/mine and /customer/{id}/pdf | Scoped owner lookup and bounded list pages | Timeline belongs to the customer; documents additionally require a company account and matching document owner | CustomerBookingHardeningTest MockMvc denies another customer's timeline and PDF | DONE |
| Payment proof and history | GET /api/payments/mine and /{paymentId}/proof; POST /api/payments/bookings/{bookingId} | Owner-scoped proof lookup and shared image validation | Customer sees only own proof and history; existing Admin review and proof access retained | CustomerBookingHardeningTest MockMvc, PaymentServiceTest, ImageUploadTest | DONE for ownership and uploads |
| Notifications | GET /api/notifications/customer/mine and /driver/{driverId}; PATCH /{id}/read | Explicit recipient identity takes precedence over legacy driver link | Mobile lists and mark-read use the session recipient; mismatched driver path yields 403 | NotificationOwnershipTest and NotificationServiceTest | DONE for mobile access |
| Driver trip list and detail | GET /api/assignments/mine and /mine/{id} | Detail includes customer phone and uses owner-scoped query | Driver session; another driver's detail is 404; superseded rows excluded | AssignmentServiceTest and MobileRouteAuthorizationTest | DONE |
| Driver trip status and photos | PATCH /api/assignments/{id}/status; GET/POST /{id}/photos; GET /{id}/photos/{photoId}/content | Strict next-state policy, mandatory same-assignment pickup/drop proof, JPEG/PNG validation | Driver owns active assignment; photo content checked using assignment plus photo ID | AssignmentServiceTest, VerificationPhotoServiceTest, AssignmentPhotoAccessTest, ImageUploadTest | DONE |
| Driver incidents | POST /api/assignments/{id}/incidents | Four typed categories, nonblank description up to 1,970 characters, unknown enum returns field error | Owner-scoped assignment; creates active Admin inbox notifications | TripMonitoringServiceTest and MobileValidationAndPaginationTest | DONE |
| Validation and pagination | Protected mobile list routes and DTO-backed writes | JSON field errors, no response stack traces, max 100 items per page | Pagination applies to mobile session responses | MobileValidationAndPaginationTest and LoginCorsHttpTest; endpoint-by-endpoint audit of all legacy DTOs UNVERIFIED | PARTIAL |

Pending or pending-verification bookings can be edited, cancelled or rejected. A confirmed booking can be cancelled by the customer only when it has no active assignment. Confirmed rescheduling remains unsupported by the customer editor. Terminal and unknown statuses reject these actions with 409. Driver status advances ASSIGNED → OTW → WAITING → PICKUP → DROP → COMPLETED, with skipped and reversed transitions returning 409 and exact repeats returning the current state without writing a milestone. The Driver pickup and drop checks occur before persistence while the assignment is locked. Existing Admin assignment operations retain their separate semantics.

## Security findings and corrections

| Finding | Evidence | Outcome |
| --- | --- | --- |
| Corporate address and contact mapping absent | entity/Customer.java:63; database/schema/BASELINE_CURRENT.sql:22 | Mapped live columns and reconciled the baseline. |
| Legacy plaintext matching | service/PasswordService.java:13 | Temporary compatibility retained. Customer, Driver, and Admin successful legacy logins save BCrypt hashes. BCrypt default strength is 10. TODO marks removal after all deployments report zero. Mobile customer upgrade has a direct security regression test. Production account counts UNVERIFIED. |
| Repeated login attempts and submitted-role logging | controller/AuthController.java:41 and :74; security/LoginThrottle.java | Shared throttling for Admin and mobile login. Five failures per normalized account or 30 per remote IP in a 15-minute window return 429. Invalid email and wrong password both return the same 401 body. Role logging removed. Limits are per server instance; cross-replica and proxy IP behavior UNVERIFIED. |
| Datasource credential fallback in committed configurations | See path-only register below | Source placeholder now requires DATABASE_PASSWORD. A launch without the environment variable returned exit 1 before Spring startup. Credential rotation and history removal UNVERIFIED. |
| Customer proof read loaded by payment ID alone | service/PaymentService.java:238 | Mobile proof reads now query by payment ID and booking customer ID. Existing Admin access remains scoped to the Admin session. |
| Driver notification path carried a user-controlled ID | controller/NotificationController.java:39; repository/NotificationRepository.java:17 | Mobile queries use session driver ID; route rejects conflicting path IDs. Recipient-role mismatch cannot be overridden by an inconsistent legacy driver FK. |
| Missing trip detail and proof enforcement | controller/AssignmentController.java:58; service/AssignmentServiceImpl.java:324 | Owner-scoped detail query, idempotent exact repeats, photo required before pickup/drop. |
| Image upload trusted different validation paths | service/ImageUpload.java; service/VerificationPhotoService.java:41; service/PaymentService.java:259 | Both upload services check JPEG/PNG magic bytes, image reader and 10 MiB limit. Files get random server names in private storage outside static roots by default. Historical PDFs remain readable through authorized proof endpoints. Deployment-specific storage overrides UNVERIFIED. |
| Live incident table has no category column | service/TripMonitoringService.java:58; database/schema/032_trip_milestones_incidents.sql:10 | Category is stored in a versioned prefix in the existing description field and decoded on read. Legacy free text is returned as OTHER. No schema change. |
| Validation returned only the first error | controller/ApiExceptionHandler.java | Retains message and adds fieldErrors. Malformed JSON and unknown incident enum values get 400 without rejected values or stack traces. Multipart limit errors receive 413. |
| Mobile arrays lacked pagination | controller/MobilePagination.java | Zero-based page, size from 1 to 100 with default 100, existing fixed sort order, and X-Next-Page when more records exist. Applies to booking list/timeline/fare history, assignment list/timeline/photos, customer and driver inboxes, customer documents, and payment history. Source lists still materialize before slicing, so the response cap does not bound SQL result size or request memory. |

### Session settings

| Setting | Effective local HTTP test or configured default |
| --- | --- |
| HttpOnly | true, verified on Set-Cookie |
| Secure | false in local HTTP test; SESSION_COOKIE_SECURE can override it. Deployed value UNVERIFIED. |
| SameSite | Strict in local HTTP test; SESSION_COOKIE_SAME_SITE can override it. Deployed value UNVERIFIED. |
| Idle timeout | 30 minutes configured; actual expiry after waiting 30 minutes UNVERIFIED |
| Session fixation | Previous session invalidated and replaced on successful login; session ID change passed the mobile login test. |

### Count-only legacy password check

Dev counts were Customer 0, Driver 0, Admin 0. The query counts non-null values that do not match the application's BCrypt encoding shape. It returns counts only.

~~~sql
SELECT 'customer' AS account_type, count(*) AS non_bcrypt
FROM customer
WHERE cust_password IS NOT NULL
  AND cust_password !~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'
UNION ALL
SELECT 'driver', count(*) FROM driver
WHERE driver_password IS NOT NULL
  AND driver_password !~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'
UNION ALL
SELECT 'admin', count(*) FROM admin
WHERE admin_password IS NOT NULL
  AND admin_password !~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$';
~~~

### Committed credential and client-key locations

Pattern search covered tracked HEAD content. The first two locations contain the prior datasource credential fallback. Firebase client API key locations follow. No private Firebase service-account key, JWT signing key or session signing key was identified by the scan. The absence of secrets in older revisions or unrecognized formats is UNVERIFIED.

- DilaDrive/backend/diladrive-api/src/main/resources/application.properties:15
- DilaDrive/backend/diladrive-api/target/classes/application.properties:13
- DilaDrive/mobile-app/android/app/google-services.json:16
- DilaDrive/mobile-app/android/app/google-services.json:18
- DilaDrive/mobile-app/android/app/google-services.json:35
- DilaDrive/mobile-app/android/app/google-services.json:37
- DilaDrive/mobile-app/lib/firebase_options.dart:44
- DilaDrive/mobile-app/lib/firebase_options.dart:53
- DilaDrive/mobile-app/lib/firebase_options.dart:61
- DilaDrive/mobile-app/lib/firebase_options.dart:70
- DilaDrive/mobile-app/lib/firebase_options.dart:79
- DilaDrive/mobile-app/web/firebase-messaging-sw.js:17

## Flutter contract changes

- Registration sends confirmPassword exactly as typed. It sends a required trimmed phone and a trimmed lowercase email. NORMAL sends name. COMPANY sends companyName, companyAddress, contactPerson and companyRegistrationNumber. The existing form labels the name input Contact Person for corporate users and adds Company Address. No password confirmation is stored or returned.
- Malaysian phone format after trimming outer whitespace is the following. It accepts a domestic zero or +60 prefix, common mobile and fixed-line lengths, and digits without embedded spaces or dashes.

  ~~~text
  (?:\+60|0)(?:11[0-9]{8}|1[02-9][0-9]{7}|3[0-9]{8}|[4-7][0-9]{7}|[89][0-9]{7,8})
  ~~~

- Passwords require 8 or more characters, at least one ASCII uppercase and lowercase letter and one digit, and at most 72 UTF-8 bytes.
- Mobile login can return 429 and Retry-After when throttled. Failed credentials continue to return 401. Wrong-role access returns 403.
- GET /api/bookings/mine/{id} and GET /api/assignments/mine/{id} are additive routes. Assignment detail includes customerPhone. Existing screen navigation still works from list results.
- Driver PATCH to PICKUP or DROP now requires uploading the matching photo first. The existing upload screen is still used. Repeating a milestone returns the current state without another event.
- Future customer payment proof uploads use JPEG or PNG only. The receipt picker removes PDF. Existing authorized downloads can still read historical PDFs.
- Driver incident request JSON now requires category (BREAKDOWN, EMERGENCY, DELAY or OTHER) and description. The existing dialog adds a category chooser with OTHER as the default. The Admin incident response adds category.
- Booking and assignment lists, timelines, fare-proposal history, inboxes, documents, payment history and photo lists retain their JSON arrays. Flutter follows X-Next-Page using page, then combines the results for existing screens. Validation errors retain message and add fieldErrors.

## Maven test runs

| Step | Passed | Failed | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Registration and schema reconciliation | 229 | 0 | 0 | 5 |
| Password compatibility, secrets, login throttle and sessions | 230 | 0 | 0 | 5 |
| a. Customer booking access and status rules | 233 | 0 | 0 | 5 |
| b. Object access and notifications | 235 | 0 | 0 | 5 |
| c. Driver assignment detail and status | 239 | 0 | 0 | 5 |
| d. Photo and payment proof uploads | 241 | 0 | 0 | 5 |
| e. Typed incident and Admin notification | 242 | 0 | 0 | 5 |
| f. Field errors and pagination | 244 | 0 | 0 | 5 |
| Final HTTP ownership and enum-contract verification | 245 | 0 | 0 | 5 |

The earlier optional PostgreSQL suites and external routing tests remain UNVERIFIED. The targeted dev PostgreSQL suite is recorded below. The Dart formatter parsed the changed Dart files. Dart analysis passed on all 14 changed application files. Flutter service and pagination tests are UNVERIFIED. Both native Windows and Chrome test invocations stopped before running tests because the pdfium_dart build hook found no configured Windows C compiler.

## Dev schema sync and follow-up fixes

| Step | Outcome | Maven passed / failed / errors / skipped |
| --- | --- | --- |
| 0. Local dev schema sync | Verified a custom-format pg_dump with pg_restore --list before writes. Applied 033_driver_identity_documents.sql and then 034_fleet_expiry_reminders.sql in one transaction against localhost:5432/DilaDrive. No destructive statements. The live catalog had all eight reported objects missing and no migration history table. | 245 / 0 / 0 / 5 at combined schema and seed checkpoint |
| 1. Dev seed and live tests | database/seed_dev_hardening.sql created one verified individual, one verified company, one pending customer, two drivers with vehicles and one admin. Synthetic bookings cover cross-account tests and a pending Admin reservation. It checks local DB identity and reads the BCrypt fixture input from DILADRIVE_DEV_SEED_HASH. Reruns can rotate fixture-only passwords. | 245 / 0 / 0 / 5 at combined schema and seed checkpoint |
| 2. Follow-up fixes | Cash route contract, milestone idempotency and photos, completed-only payments, category-only mobile requests, normalized customer email, and shared conflict check for legacy edits. An intermediate run had one test-only Mockito stubbing error. It was corrected. | 249 / 0 / 0 / 5 |

Backup archive path is C:\Users\syafi\AppData\Local\Temp\DilaDrive-dev-before-033-034-a216c57ff20e4b199af444b3ced1c257.dump. It contains dev data. The archive inventory was verified; a restore was not run.

Hibernate initially reported driver.driver_icno_lookup as CHAR(64) while the entity expected VARCHAR. The JDBC CHAR mapping resolved the error. A subsequent startup encountered sandbox trust-store access failure, then the backend started with Hibernate validation enabled and no mapping error.

Live seeded HTTP tests confirmed 404 for cross-customer booking detail, cross-driver trip detail and cross-driver status changes. An owner's out-of-order pickup returned 409. The Admin assignment preview and suggested-assignment endpoints returned 200 and PostgreSQL confirmed one matching fixture hold. Test-only accounts and rows remain in dev.

### Follow-up behavior

- Cash already shared the Admin/Driver interceptor allowlist in this checkout. A new MockMvc regression verifies both roles reach the controller, with the correct actor passed to the service.
- Pickup and drop require same-assignment photos for both Admin and Driver PATCH callers. Exact repeats return the current state and create no new event. Order and ownership checks still apply to driver changes.
- Bank, card and cash require booking status COMPLETED. The Flutter payment screen enables submission only then. The 6% card surcharge is unchanged and remains an **UNVERIFIED business rule**.
- A supplied preferredVehicleId on the mobile TripQuoteRequest returns 400. The existing Flutter app already sends a category only. Admin controls its selected pair using the separate reservation request.
- Legacy pending edits use CustomerTripService.conflictReason with the edited booking excluded. The legacy request has no route duration, so a configurable 240-minute provisional interval is used by default. This is a schedule check; the legacy editor still does not create a hold.
- Customer login and registration normalize trimmed lowercase email. The repository recognizes stored values with outer spaces. The live normalized duplicate count was 0 groups. No index was applied.

Email index definition, now applied on dev only

~~~sql
CREATE UNIQUE INDEX uq_customer_email_normalized
    ON public.customer (lower(btrim(cust_email)))
    WHERE cust_email IS NOT NULL AND btrim(cust_email) <> '';
~~~

## PostgreSQL ownership, profile and push verification

The schema sync, backup, seed, cash-route regression, completed-only payments, customer vehicle preference rejection, email index proposal, and legacy pending-edit conflict check were completed in the preceding pass. The email index was subsequently approved and applied on dev only, as recorded below.

DevHardeningPostgresTest runs the real Spring MVC, service, JPA and PostgreSQL stack against the seeded loopback DilaDrive database. It uses the published seed fixture IDs resolved by their synthetic identities. Additional invoices, payments, photo records, notifications, and token assertions run in a transaction that rolls back after each test. Firebase delivery is disabled. The final Maven run with RUN_DEV_HARDENING_IT=true reported **254 passed, 0 failed, 0 errors, 5 skipped**, including all four PostgreSQL tests. A separate read-only query found zero temporary invoices, proofs, photos, notifications and push tokens remaining after rollback. To run it, set RUN_DEV_HARDENING_IT=true and supply DATABASE_PASSWORD through the environment, then run mvn test in backend/diladrive-api. The other optional PostgreSQL suites remain UNVERIFIED.

| Area | Verified test coverage |
| --- | --- |
| Customer IDOR | Owner booking and timeline positive controls. Another customer receives 404 for booking detail, timeline, corporate document PDF and payment proof. Real scoped document and payment queries find the seeded owner rows. |
| Driver IDOR | Other driver cannot read assignment detail, change trip status, list photos, read photo content or upload a photo. The session driver's inbox omits the other driver's records and cannot mark those records read; the owning driver can mark their own read. |
| Push tokens | Session determines the role and recipient for registration and removal, regardless of extra payload properties. An existing owner can refresh the registration. A different recipient gets 409 and cannot remove the live token. The owner can register a new token and remove the previous one. The atomic PostgreSQL conflict predicate prevents takeover of an existing binding, including concurrent registration attempts. Actual FCM delivery remains UNVERIFIED. |
| Mass assignment | Real endpoint tests send forged IDs, roles, status, email-verification and approval properties to PUT /api/customers/me, PUT /api/drivers/me and POST /api/drivers/me/password. Profile changes target the session identity, customer approval/status stay intact, the driver remains ACTIVE, and only the session driver's BCrypt password changes. |

### Effective credentialed CORS

- Development and local defaults allow exactly http://localhost:5173 and http://127.0.0.1:5173. The base default for other profiles is http://localhost:5173.
- CORS_ALLOWED_ORIGINS can override these values with explicit comma-separated origins. Configuration rejects wildcard hosts and ports, including a bare wildcard. Credentials are enabled. Allowed methods are GET, POST, PUT, PATCH, DELETE and OPTIONS. The pagination header X-Next-Page is exposed and preflight cache max age is 3,600 seconds.
- Real HTTP tests exercise the allowed origins and an unlisted loopback port. For another Flutter web port, configure that exact origin before starting the API. Deployed overrides remain UNVERIFIED.

### Build outputs and credential history

Added /backend/diladrive-api/target/ to DilaDrive/.gitignore. Ran git rm -r --cached -f -- DilaDrive/backend/diladrive-api/target. The force flag was needed because existing generated files had staged and working-tree differences. Verified that Git tracks zero paths under target and the files remain on disk. The staged removals have not been committed.

The old datasource fallback is present in committed history in the source and generated application.properties paths listed above. No credential value was copied into the report and history was not rewritten.

### Flutter Windows native build

The project resolves pdfium_dart 0.4.2. Its hook builds the native pdfrx_pdfium_gate C shared library on the host before bundling PDFium. On this machine the Flutter runner stops before discovery with “No compiler configured on host windows_x64”. Neither cl.exe nor Visual Studio Installer's vswhere.exe was available.

The required Windows toolchain is Visual Studio 2022 Build Tools with Desktop development with C++ (Microsoft.VisualStudio.Workload.VCTools), MSVC v143 x64/x86 build tools, and a Windows 10 or Windows 11 SDK. Open the x64 Native Tools Command Prompt, verify where cl and where link, then run flutter test --no-pub --reporter expanded from mobile-app. A cold native-asset cache also needs access to the PDFium GitHub release used by the installed hook. [Microsoft workload IDs](https://learn.microsoft.com/en-us/visualstudio/install/workload-component-id-vs-build-tools?view=vs-2022) · [x64 command-line setup](https://learn.microsoft.com/en-us/cpp/build/how-to-enable-a-64-bit-visual-cpp-toolset-on-the-command-line)

Flutter invocation result was 0 tests executed. Pass and failure counts remain UNVERIFIED until the toolchain is installed. No app dependencies were changed.

## Approved dev email index and fixture cleanup

The local dev connection resolved to DilaDrive on loopback ::1:5432. A fresh custom-format pg_dump was verified with pg_restore --list before the transaction. Backup path:

    C:\Users\syafi\AppData\Local\Temp\DilaDrive-dev-before-email-index-2eda99e998d24106a20ab36b7a237633.dump

The transaction created public.uq_customer_email_normalized exactly as defined above. The catalog reports it unique and valid. A rollback-only test attempted to use an existing email with changed case and surrounding whitespace and verified that this index rejected it. No other database was targeted. A backup restore was not run.

The seed script and existing fixtures now use DEV-SEED labels for accounts, vehicle models and booking addresses. The stable synthetic email addresses and plate numbers are retained for the tests. database/cleanup_dev_hardening.sql requires the expected dev connection, markers and exact identities. It stops on unexpected references, checks deferred constraints, and defaults to rollback. The verified dry run selected exactly 1 hold, 2 assignments, 3 bookings, 2 vehicles, 2 drivers, 3 customers and 1 admin. The fixtures remain available for integration tests.

Run from the DilaDrive project directory. Supply the connection password through the normal PostgreSQL credential mechanism.

~~~powershell
psql -X -h localhost -p 5432 -U postgres -d DilaDrive -f database/cleanup_dev_hardening.sql
psql -X -h localhost -p 5432 -U postgres -d DilaDrive -v apply=true -f database/cleanup_dev_hardening.sql
~~~

The first command previews and rolls back. The second commits cleanup. Committed cleanup was not run.

### Test and tooling recheck

The Maven rerun with RUN_DEV_HARDENING_IT=true passed **254**, failed **0**, errored **0**, and skipped **5**. All four rollback-only DevHardeningPostgresTest cases passed against the renamed fixtures. They cover Customer and Driver IDOR with real queries, push registration/replacement/removal ownership, and protected fields on Customer/Driver profile and Driver password endpoints. The CORS regression remains green, credentialed origins remain explicit, and Git still tracks zero paths in the ignored backend target directory. No history rewrite was performed.

| Skipped test | Actual Surefire reason |
| --- | --- |
| LiveRoadRouteTest.uitmToKliaThroughAuthenticatedHttpEndpoint | RUN_LIVE_ROAD_ROUTE was unset. |
| LiveJasinRoutingTest.actualJasinToKliaProviders | RUN_LIVE_JASIN_ROUTE was unset. |
| LiveLocationFlowTest.KLIAtoKLSentralUsesRealSuggestionsAndDrivingGeometry | RUN_LIVE_LOCATION_FLOW was unset. |
| LiveLocationGatewayTest.liveSearchAndReverse | RUN_LIVE_GEOCODING_TEST was unset. |
| StrictAvailabilityAuditTest.traceActualFleetRequestWithoutChangingCompanyData | RUN_STRICT_AVAILABILITY_AUDIT was unset. |

All five opt-in diagnostics remain UNVERIFIED. Flutter was rerun with flutter test --no-pub --reporter expanded and executed **0 tests**. The pdfium_dart hook still fails before discovery because Windows has no configured C compiler. The current Windows session lacks an administrator token and the Ubuntu WSL distribution has no native compiler or Linux Flutter SDK. The required Visual Studio 2022 C++ workload, MSVC v143 x64/x86 tools and Windows SDK are documented above. Flutter pass/fail counts remain UNVERIFIED.

## Legacy request validation inventory

This is a source and test review. UNVERIFIED here refers to the specific input boundaries in the last column. Existing ownership, state, or happy-path tests still count for the behavior they exercise. Paths below are relative to backend/diladrive-api/src/main/java/com/diladrive unless otherwise linked.

| Controller and endpoint | Request file or binding | Existing checks and coverage | Remaining UNVERIFIED input boundaries |
| --- | --- | --- | --- |
| controller/BookingController.java:190 · PUT /api/bookings/{id}/customer | dto/CustomerBookingRequest.java:11, service/BookingService.java:504 | Bean validation covers required fields, string limits and minimum counts. The service checks supported trip/category, schedule, luggage combinations and shared conflicts. Service tests exercise edit, ownership and conflict. | HTTP-level malformed body and field-error coverage for this legacy route. Passenger and luggage maxima are fleet-dependent. Flight-number format, control characters and use of a nonpositive booking ID have no verified rejection contract. |
| controller/CustomerController.java:63 · PUT /api/customers/me | dto/CustomerProfileUpdateRequest.java:7, service/CustomerService.java, service/ProfileUpdateValidation.java | DTO requires a name and email and caps field sizes. Profile validation checks phone, corporate fields and profile image. PostgreSQL mass-assignment tests and profile service tests cover ownership, permitted fields and duplicate email. | Full HTTP boundary matrix for names, phone variants, trimming, corporate blanks, and invalid or oversize profile images. The profile phone rule differs from registration. |
| controller/DriverController.java:41 · PUT /api/drivers/me | dto/DriverProfileUpdateRequest.java:7, service/DriverService.java, service/ProfileUpdateValidation.java | DTO requires name, email and phone and caps field sizes. Service normalizes profile fields and validates phone and image. PostgreSQL tests verify the target identity and protected fields. | Full HTTP field-error matrix for name, email, phone, profile image and their length/format limits. |
| controller/DriverController.java:48 · POST /api/drivers/me/password | dto/ChangePasswordRequest.java, service/DriverService.java:113 | Legacy DTO has no Jakarta constraints, and the controller does not invoke Bean Validation. The service validates the current credential and the new password, including its length. The successful change and rejection of forged identity fields are covered by PostgreSQL tests. | HTTP matrix for missing or null fields, malformed JSON, length and byte boundaries, and expected composition policy. |
| controller/AuthController.java:74 · POST /api/auth/mobile/login | dto/MobileLoginRequest.java, service/MobileAuthService.java | Role pattern, service-side credential checks, normalized email and throttling. Login and fixation tests cover credential failure and session replacement. | DTO-level required-field and size behavior for email/password, and HTTP field errors for absent or malformed role. Invalid credentials still use the generic failure contract. |
| controller/BookingController.java:108 · GET /api/bookings/availability/recommendation and :153 · GET /api/bookings/availability | Scalar query parameters | Both routes are retired and return 410 after binding and authentication. The recommendation route first checks the trip type and requires a luggage-size value when luggage is present. | Invalid scalar binding and retired-route 410 behavior were not rerun in this review. No active service-side field validation needs to be added to these routes unless they are restored. |
| controller/PaymentController.java:36 · POST /api/payments/bookings/{bookingId} | Multipart scalar parameters and file, service/PaymentService.java:85 | Service validates payment method, amount/scale and booking eligibility, and shares image validation. Ownership, rejection before COMPLETED and image checks have test coverage. | Complete multipart HTTP negative-input matrix, including absent or malformed amount or method, exact amount bounds and missing proof. There is no multipart DTO. |
| controller/AssignmentController.java:109 · PATCH /api/assignments/{id}/status and :164 · POST /api/assignments/{id}/photos | dto/AssignmentStatusRequest.java:5; scalar photoType/file, service/AssignmentServiceImpl.java and service/VerificationPhotoService.java | Status length and service allowlist, transition/photo rules, upload size/signature checks and cross-driver authorization have coverage. | HTTP validation matrix for blank/unknown status, malformed or nonpositive IDs, missing multipart parts and unsupported photoType. |
| controller/LocationController.java:21 · GET /api/locations/search and :29 · GET /api/locations/reverse | Scalar query parameters, service/LocationGatewayService.java:54 and :76 | Gateway service validates input and existing unit tests cover gateway behavior. External live geocoding diagnostics were skipped. | Endpoint-level malformed query, coordinate and range coverage during live provider execution. |

The deprecated createCustomerBooking method at controller/BookingController.java:144 is an internal adapter with no HTTP mapping. The newer dto/TripQuoteRequest.java is separate from the legacy booking DTO. Its category-only rule and several booking access checks are tested. Its complete HTTP field-boundary matrix, including coordinate extremes, flight/date combinations and lead-time behavior, remains UNVERIFIED. Bodyless read endpoints do not have request DTOs. Their ownership checks are reported separately. Admin-only customer/driver mutations were outside this mobile validation inventory.

Read-only recheck confirmed public.uq_customer_email_normalized remains unique and valid on local dev DilaDrive via loopback and the backup archive still exists at the path above. Three DEV-SEED customer accounts were present. This review did not rerun the index migration, execute the cleanup script, or run a new test suite. The previously verified five skip reasons are listed above.
