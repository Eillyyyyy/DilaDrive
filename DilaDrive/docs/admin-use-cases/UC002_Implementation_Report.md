# UC002 implementation report

Admin, Driver and Customer each fetch fresh account data on opening their account view. Spring Boot checks persisted driver activation before protected requests. A disabled driver receives a structured rejection that Flutter uses to clear the local session and return to Login.

## Changed files

Paths are relative to the workspace root.

| File Path | Function / Method | Change Made | Reason | Test Result |
|---|---|---|---|---|
| `diladrive/web-admin/src/App.jsx` | `App`, profile session effect, `renderPage` | Fresh GET per opening and retry, loading and distinct failure messages, request cancellation and Login on 401. | Show current details while preserving sessions during temporary failures. | Build and Chrome smoke passed. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/AdminService.java` | `getProfile(Long)`, `findAdmin(Long)`, `requireActiveAdmin` | Return 404 for missing self-profile, keep inactive at 401. | Distinguish a missing row from session rejection without changing existing update paths. | PostgreSQL tests passed. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/config/DriverSessionConfig.java` | `driverSessionInterceptor` | Map the shared guard to API routes. | Cover every request carrying a Driver session, including shared endpoints. | PostgreSQL route coverage passed. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/security/DriverSessionGuard.java` | `preHandle` | Recheck current account activation and revoke only Driver mobile identity on rejection. | Prevent stale sessions from bypassing account status. | PostgreSQL tests passed. |
| `diladrive/mobile-app/lib/services/api_http_client.dart` | `createApiHttpClient`, `_SessionAwareClient.send` | Detect the structured 403 and preserve the response for the calling service. | Handle deactivation consistently for ordinary and multipart requests. | Flutter transport test passed. |
| `diladrive/mobile-app/lib/services/mobile_session.dart` | `storeResponseCookies`, `clear`, `revokeDriverSession` | Track session generation and clear the matching Driver session. | Prevent delayed deactivation responses from revoking a newer login. | Flutter transport test passed for deactivation and other error codes. |
| `diladrive/mobile-app/lib/main.dart` | `main`, `_returnDeactivatedDriverToLogin`, `DilaDriveApp.build` | Wire the session callback to root navigation and the message banner. | Remove protected screens from the navigation stack on deactivation. | Analysis passed. Android navigation requires manual verification. |
| `diladrive/mobile-app/lib/controllers/driver_profile_controller.dart` | `load` | Suppress overlapping loads on the same controller. | Prevent duplicate in-flight requests. | Driver profile tests passed. |
| `diladrive/mobile-app/lib/screens/driver/view_account.dart` | `_DriverViewAccountPageState.build` | Replace the blank fallback with an empty-state message and Retry. | Give the user a way to recover. | Driver profile tests passed. |
| `diladrive/mobile-app/lib/screens/customer/profile.dart` | `_ProfilePageState.build`, `_ProfileHeader.build` | Pass the profile service when opening account details and reuse the safe avatar. | Fetch fresh details and handle malformed images on both screens. | Flutter widget and analysis checks passed. |
| `diladrive/mobile-app/lib/screens/customer/view_account.dart` | `initState`, `_retry`, `build`, `_accountDetails`, `CustomerProfileAvatar` | Fetch once per new route, render loading/errors/retry and use an image fallback. | Display current details, including conditional corporate fields. | Flutter widget test passed. |
| `diladrive/backend/diladrive-api/src/test/java/com/diladrive/CustomerTripPostgresIT.java` | `selfProfileReadsUseCurrentDatabaseRowsAndHideCredentials`, `driverDisabledAfterSessionEstablishmentCannotReachProtectedHandlers`, `mobileSession` | Added profile reads and post-login revocation cases against real PostgreSQL. | Verify freshness, identity, state transitions and protected routes. | 31 integration tests passed. |
| `diladrive/mobile-app/test/view_account_freshness_test.dart` | Widget and shared-client tests | Added reopening, loading, retry, malformed image, conditional fields and deactivation response coverage. | Exercise the UC002 behavior directly. | Both new tests passed. |
| `diladrive/docs/admin-use-cases/UC002_View_Account.md` | UC002 flow and inventory | Updated rules, flows and API documentation. | Reflect the approved implementation. | Cross-checked against source. |
| `diladrive/docs/admin-use-cases/UC002_Implementation_Report.md` | Implementation report | Recorded verification and limitations. | Keep an audit trail for Phase 2. | Reviewed against test output. |

## Fresh data retrieval

- **Admin** · Opening Profile or choosing Retry calls `GET /api/auth/session`. The effect depends on whether Profile is open, the retry counter and the stable user ID. Switching away aborts an outstanding request. The Chrome smoke exercised restoration, open, retry, close, reopen and session rejection with exactly five expected requests.
- **Driver** · The existing detail route loads `GET /api/drivers/me` from `initState`. The summary and detail are separate reads. The same controller suppresses overlapping loads and keeps pull-to-refresh.
- **Customer** · The account route calls `GET /api/customers/me` independently of the parent snapshot. It reuses the parent service rather than constructing another transport. Rebuilds reuse the pending future. Retry or reopening starts a fresh read.

## Disabled Driver Security

`DriverSessionGuard.preHandle` reads the server session's mobile role and ID, loads the current `driver` row and requires canonical `driver_status = ACTIVE`. It covers all `/api/**` handler requests for Driver sessions except login, logout, registration and preflight. Existing endpoint checks still enforce ownership and actor permissions.

| Protected area | Driver endpoints reviewed and exercised by the deactivation test |
|---|---|
| Profile | `GET /api/drivers/me`, `PUT /api/drivers/me`, `POST /api/drivers/me/password` |
| Trips and assignments | `GET /api/assignments/mine`, `GET /api/assignments/{id}`, `PATCH /api/assignments/{id}/status` |
| Proof of service | `GET /api/assignments/{id}/photos`, multipart `POST /api/assignments/{id}/photos`, `GET /api/assignments/{id}/photos/{photoId}/content` |
| Cash recording | `POST /api/payments/bookings/{bookingId}/cash` |
| Notifications | `GET /api/notifications/driver/{driverId}`, `PATCH /api/notifications/{id}/read` |
| Push registration | `POST /api/mobile/push-token`, `DELETE /api/mobile/push-token` |

The first blocked request receives **403** with JSON code `DRIVER_ACCOUNT_DEACTIVATED` and the contact-admin message. Removing only the mobile identity means later calls with the old session fail normal authorization. The integration test verified that a separate admin identity on the same test session was preserved. Customer-role sessions bypass the Driver check. The shared Flutter client clears the matching local session, and the root navigator opens Login with the message. A temporary database failure uses the existing error handler and does not produce the deactivation code.

## Regression Testing

| Existing workflow | Evidence |
|---|---|
| UC001 Create Driver Account | Existing Maven driver creation and linked vehicle tests passed. |
| UC001 Create Admin Account | Existing Maven SUPER_ADMIN creation and unauthorized-role tests passed. |
| Customer Registration | Maven registration tests and Flutter registration tests passed. |
| Admin View Booking and Verify Booking | Existing Maven booking tests and PostgreSQL confirmation and assignment scenarios passed. Browser verification of the booking pages was outside the smoke test. |
| Customer Create Booking | PostgreSQL customer trip suite passed, including active creation and denial for pending and rejected customers. |
| Active Driver trips and assignments | PostgreSQL active-session listing and existing assignment tests passed. Flutter trip and proof-of-service service tests passed. |

## Commands and results

| Check | Result |
|---|---|
| `npm run build` with temporary output | Passed. |
| Chrome headless smoke against mocked session responses | Passed. Verified loading, fresh data, retry, 503 and 404 session preservation, and Login on 401. |
| `mvn -o -B test` | 216 passed, 5 skipped, zero failures or errors. |
| `mvn -o -B -Dtest=CustomerTripPostgresIT test` with `RUN_CUSTOMER_TRIP_IT=true` | 31 passed in a disposable PostgreSQL schema, zero failures or errors. |
| `flutter analyze --no-pub` | Passed with no issues. |
| Selected Flutter tests | 31 passed and 1 failed. The Update Account request-body test rejects the existing `profileImage: null` field. Its failure was reproduced with the pre-UC002 service, session and transport code. |
| Standard Windows Flutter test execution | The PDFium native hook required a Windows C compiler. Tests were run in a temporary project copy with only that unused hook omitted. No repository dependency changes. |

Android end-to-end deactivation navigation and visual layout require manual verification. No schema changes were required.
