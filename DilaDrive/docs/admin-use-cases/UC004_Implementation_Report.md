# UC004 Implementation Report

Active Admins can approve or reject pending Individual and Corporate customers. The dedicated endpoint records the reviewer and time, writes a history record, and creates an unread Customer notice atomically. The old profile edit cannot change customer status.

## File-by-file record

Paths are relative to the workspace root.

| File Path | Function / Method | Change Made | Reason | Test Result |
|---|---|---|---|---|
| `diladrive/web-admin/src/App.jsx` | clearAdminSession, renderPage | Passes a stable session-expiry callback to Customers. | Reuse existing Login handling. | React build and browser check. |
| `diladrive/web-admin/src/pages/Customers.jsx` | StatusBadge, fetchCustomers, askToVerify, handleVerify | Adds exact filters, corporate review, confirmation, reason, PATCH, retry/reconciliation and feedback. | Keep decision writes separate from contact edits. | React render and browser checks. |
| `diladrive/web-admin/tests/customer-verification.test.cjs` | render and three cases | Tests status filters, corporate data and disabled controls. | Cover decision UI branches. | Three added tests passed. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/controller/CustomerController.java` | verifyCustomer, constructor | Adds dedicated PATCH and resolves reviewer from session. | Restrict the HTTP contract. | PostgreSQL HTTP tests. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/dto/CustomerVerificationRequest.java` | record, rejectUnknownField | Allows decision and bounded reason and rejects extra JSON keys. | Avoid client-supplied authority or customer status. | Validation and forged-field tests. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/dto/CustomerVerificationResponse.java` | record | Returns the safe verification receipt. | Provide outcome and metadata. | HTTP response assertions. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/dto/CustomerProfileResponse.java` | from, compatibility constructor | Adds owner-visible reason for REJECTED only. | Allow authenticated customers to read their result. | Ownership/profile tests. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/entity/Customer.java` | recordVerification, getRejectionReason | Maps metadata and enables dynamic updates. Raw entity JSON ignores the reason. | Isolate verification writes and keep reason private. | Database and response tests. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/repository/CustomerRepository.java` | findByIdForUpdate | Reads the target with a pessimistic lock. | Serialize competing decisions. | Concurrency integration. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/CustomerVerificationService.java` | verify | Checks active Admin and transition, writes metadata/history/inbox atomically. | Make decisions safe and auditable. | PostgreSQL authorization, rollback and concurrency. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/CustomerService.java` | updateCustomer | Rejects status supplied to generic edit. | Close the legacy bypass. | HTTP bypass and ordinary-edit tests. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/NotificationService.java` | createCustomerNotification, markAdminNotificationAsRead | Schedules a generic push body for verification notices and protects customer inbox items from the admin mark-read path. | Keep the reason inside the protected inbox. | Inbox ownership and push test. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/BookingPushService.java` | notifyAfterCommit | Handles a rejected executor submission without surfacing it after commit. | Keep optional push separate from success. | Simulated push failure integration. |
| `diladrive/database/schema/028_customer_account_verification.sql` | DDL | Adds customer metadata and decision-history table with foreign keys and index. | Preserve history without rewriting existing rows. | Applied in test schema. |
| `diladrive/database/schema/README.md` | Migration 028 instructions | Documents migration order and manual command. | API validates existing schema on startup. | Reviewed against code and test setup. |
| `diladrive/mobile-app/lib/models/customer_profile.dart` | CustomerProfile.fromJson | Parses rejection reason. | Display the authenticated customer's result. | Flutter profile regression checks. |
| `diladrive/mobile-app/lib/screens/customer/profile.dart` | build | Uses shared status/reason panel and Profile refresh. | Display the decision beside account details. | Analysis and shared widget coverage. |
| `diladrive/mobile-app/lib/screens/customer/view_account.dart` | _accountDetails, CustomerVerificationStatus.build, _accountStatusLabel | Displays three statuses, conditional reason and Check status. | Expose latest result without status edits. | Flutter widgets. |
| `diladrive/mobile-app/lib/screens/customer/create_booking.dart` | _VerificationRequired.build | Shows rejection state and adds live recheck for pending/rejected accounts. | Allow status refresh while preserving the booking gate. | Flutter booking access tests. |
| `diladrive/mobile-app/test/view_account_freshness_test.dart` | account refresh widget case | Covers status and rejection reason after fresh GET. | Verify result display and refresh. | Focused Flutter suite. |
| `diladrive/mobile-app/test/create_booking_access_test.dart` | rejected customer access case | Checks re-fetch and booking denial. | Catch rejected account navigation regressions. | Focused Flutter suite. |
| `diladrive/mobile-app/test/customer_notification_widget_test.dart` | verification notice case | Checks both messages, reason and mark-read. | Verify existing inbox rendering. | Focused Flutter suite. |
| `diladrive/backend/diladrive-api/src/test/java/com/diladrive/CustomerTripPostgresIT.java` | decision integration scenarios and database fixture | Applies 028 in a disposable schema and tests decisions, privacy, failures and compatibility. | Verify real writes and one winning decision. | PostgreSQL integration suite. |
| `diladrive/backend/diladrive-api/src/test/java/com/diladrive/LiveRoadRouteTest.java` | database fixture | Applies migration 028 in baseline-backed fixture. | Keep opt-in schema compatible. | Compiled. Separate opt-in suite not run. |
| `diladrive/backend/diladrive-api/src/test/java/com/diladrive/PricingPostgresIT.java` | database fixture | Applies migration 028 in baseline-backed fixture. | Keep opt-in schema compatible. | Compiled. Separate opt-in suite not run. |
| `diladrive/backend/diladrive-api/src/test/java/com/diladrive/controller/CustomerControllerTest.java` | controller construction | Supplies the verification service mock. | Preserve existing test setup. | Maven suite passed. |
| `diladrive/backend/diladrive-api/src/test/java/com/diladrive/controller/CustomerOnlyCreationTest.java` | controller construction | Supplies the verification service mock. | Preserve existing test setup. | Maven suite passed. |
| `diladrive/backend/diladrive-api/src/test/java/com/diladrive/controller/RecordDeletionTest.java` | controller construction | Supplies the verification service mock. | Preserve existing test setup. | Maven suite passed. |
| `diladrive/docs/admin-use-cases/UC004_Verify_Account.md` | UC004 specification | Updated the audit to the implemented workflow. | Document API, status and permissions. | Cross-checked with source. |
| `diladrive/docs/admin-use-cases/UC004_Implementation_Report.md` | Implementation report | Records exact files, commands, results and deployment checklist. | Provide verification and review evidence. | Cross-checked with test output. |

## Deployment and database

Apply migration 028 to the existing configured database and schema after the preceding migrations and before starting this API version. The API uses schema validation by default; it does not run the forward migration automatically. Verify the target schema and follow the deployment backup procedure. From the repository root:

```powershell
psql -X -v ON_ERROR_STOP=1 -d <configured-database> -f diladrive/database/schema/028_customer_account_verification.sql
```

Use the same credentials and schema selection as the application. Migration 028 adds nullable customer metadata, a history table and an index. Existing rows, statuses and accounts are preserved. Reviewer/timestamp/reason remain null on legacy rows unless an eligible decision is made. No live database migration was performed in this task. PostgreSQL testing used a disposable schema and its own fixtures.

## Contract and authorization

- `PATCH /api/customers/{id}/verification` takes `APPROVE` or `REJECT`. Reject requires a nonblank trimmed reason, at most 500 characters. Approve carries no rejection reason. Extra fields are rejected. The receipt returns customer ID/category, new status, decision, reviewer ID and timestamp, plus rejection reason if applicable.
- The server derives reviewer identity from the session and reloads their Admin record. Active ADMIN and SUPER_ADMIN are allowed. Missing session is 401, missing/inactive/unsupported reviewer is 403, missing target is 404 and invalid or repeated transitions are 409.
- Generic `PUT /api/customers/{id}` returns 400 when `custStatus` is supplied as a nonnull value. Normal Admin detail updates remain available. Existing UC003 self-edit DTO excludes status, reason and category.
- React shows exact statuses, including unknown values as their own status. It exposes decisions only for PENDING, validates reason, prevents concurrent clicks, and refreshes the list/details after success, conflict or an ambiguous error. Request failure requires an explicit user action for a future attempt.
- Flutter shows verification state and reason on the customer's own Profile and View Account. The existing inbox renders both outcomes, including the reason on rejection. Sign-in and UC003 profile editing remain available to rejected customers. The booking backend still requires ACTIVE.

## Transaction, concurrency and notification evidence

A single transaction locks the Customer row, checks PENDING, updates only decision columns, inserts history through the same data source and calls the existing transactional Customer notification service. The real PostgreSQL concurrency test issued competing approve/reject calls and observed one 200, one 409, one history row and one inbox row. The rollback test deliberately rejected notification insertion with a constraint in the disposable schema. Customer stayed PENDING and history/inbox rows were absent.

The owner-scoped inbox and mark-read routes were tested with two separate customer sessions. Optional push is scheduled after commit and carries a generic body. A test forced the push infrastructure lookup to fail and observed the stored decision and notice from another connection. This is simulated failure coverage. No actual FCM delivery, device token or real notification permission flow was tested.

## Test results

| Command or check | Result |
|---|---|
| `npm run build -- --outDir <temporary directory>` | Passed. |
| `node --test tests/*.test.cjs` | 32 passed, zero failures or skips. |
| Headless Chrome with local mocked API | Passed. Checked filters, company details, required reason, duplicate-click protection, approval and rejection, 409 and 500 reconciliation, and 401 sign-out. |
| `mvn -o -B test` | 216 passed, 5 skipped, zero failures or errors. |
| `RUN_CUSTOMER_TRIP_IT=true` with `mvn -o -B -Dtest=CustomerTripPostgresIT test` | Passed. 43 passed, zero failed or skipped. Migration 028 ran in the disposable schema. |
| `flutter analyze --no-pub` | Passed, no issues. |
| Focused and full `flutter test --no-pub --reporter expanded` | Focused suite passed all 22 tests. Full suite had 152 passes and 8 pre-existing booking/dashboard failures reproduced before UC003. |

All Flutter widget/unit tests were run in a temporary project copy with the unused PDFium native build hook omitted due to the host's Windows C compiler limitation. Repository dependencies were preserved. The existing UC003 baseline booking/dashboard failures are distinguished from UC004 results.

The Maven and PostgreSQL suites include UC001 creation/registration, UC002 profile reads and disabled-driver guard, UC003 self-edit and profile validation, ACTIVE booking, rejected/pending booking restrictions, Admin booking verification and assignment regression. Flutter targets include Customer registration, View Account, the verification inbox, booking access and profile edits. Separate environment-gated integration and live routing suites were not run.

## Manual verification

1. Apply migration 028 to the intended application schema, start the API, and confirm Customer list and Profile run against it.
2. On a test deployment, register an Individual and a Corporate customer. Approve one and reject the other. Verify Admin list refresh and masks for non-pending decisions.
3. On Android, sign in as the rejected customer, check the reason in Profile, View Account and Inbox, mark the notice read, edit an allowed field and confirm booking is blocked.
4. If FCM is enabled in the deployment, verify device token registration and notification delivery separately. Confirm a customer can still read the persisted notice when push is disabled.

The workflow does not provide reconsideration or resubmission. Legacy rejection reasons are absent if never captured. Production deployment, physical Android and live FCM verification remain open.
