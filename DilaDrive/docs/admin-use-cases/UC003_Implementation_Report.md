# UC003 implementation report

UC003 adds fresh edit-time reads, preservation of unchanged optional fields, corporate validation and safe mobile cancellation. The existing self-profile URLs and session ownership checks are used. Customer booking eligibility, account creation, verification, Driver activation and vehicle assignment rules remain in their existing workflows.

## File-by-file record

Paths are relative to the workspace root.

| File Path | Function / Method | Change Made | Reason | Test Result |
|---|---|---|---|---|
| `diladrive/web-admin/src/App.jsx` | `clearAdminSession`, `loadEditProfile`, `handleSaveProfile`, `renderPage` | Added a cancellable edit GET and 15-second edit request timeouts. Edit GET/PUT 401 clears local auth. | Fetch fresh data and preserve sessions on temporary failures. | Chrome smoke and React build passed. |
| `diladrive/web-admin/src/pages/Profile.jsx` | `openEditor`, `handleChange`, `handleCancel`, `handleImageChange`, `handleSubmit` | Added loading/Retry, controls for explicit clears, optional-field comparison and a duplicate-save guard. Removed role from payload. | Preserve newer optional data and keep compliant fields editable. | Chrome smoke passed, including exact request keys and recovery after a failed save. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/dto/ProfileUpdateRequest.java` | `isClearPhone`, `setClearPhone`, `isClearAddress`, `setClearAddress` | Added explicit optional clear flags. | Separate blank/null input from intentional clearing. | PostgreSQL omission and clear cases passed. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/dto/CustomerProfileUpdateRequest.java` | Record and compatibility constructor | Added `clearPhone` with false default on the existing constructor. | Explicit customer phone removal. | Maven and PostgreSQL tests passed. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/ProfileUpdateValidation.java` | `phone`, `mobileImage`, `customerImage`, `hasWebpFrame` | Shared phone and mobile image checks. Customer validates PNG/JPEG with JDK decoding and WebP with RIFF and frame checks. | Reject malformed data within the profile update scope. | PostgreSQL accepted a valid PNG and rejected a truncated PNG. Existing Driver image tests passed. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/AdminService.java` | `updateProfile(Long)` | Preserve absent optional fields, respect explicit clear and validate supplied phone. | Avoid clearing existing profile values on omitted input. | Protected attributes, duplicate email, invalid phone, explicit clear and expiry tests passed. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/DriverService.java` | `updateProfile` | Use shared phone/image validation with the existing explicit setters. | Align validation and preserve Driver-owned field restrictions. | Maven Driver tests and PostgreSQL active/deactivated requests passed. |
| `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/CustomerService.java` | `updateCustomerProfile` | Check effective corporate values and validate photo and phone before setting fields. Preserve omissions. | Apply required-company policy, preserve data and reject malformed images. | PostgreSQL tested NORMAL, PENDING, COMPANY, blanks, image cases and preserved state. |
| `diladrive/mobile-app/lib/screens/customer/profile.dart` | `_editProfile` | Pass the existing service to the editor. | Fetch a fresh account rather than passing the summary snapshot. | Flutter analysis and focused widget tests passed. |
| `diladrive/mobile-app/lib/screens/customer/update_account.dart` | `initState`, `_load`, `_chooseProfileImage`, `_save`, `_cancel`, `_editForm` | Added fresh read, Retry, safe avatar and image selection, field comparison, validation, explicit clear and success/discard flows. | Keep form data current and protect unchanged optional values. | Customer edit widget and serialization tests passed. |
| `diladrive/mobile-app/lib/screens/driver/update_account.dart` | `_loadProfile`, `_save`, `_cancel`, `build`, `_field` | Retained fresh read and photo tracking. Added discard confirmation, Back handling, duplicate guard and phone validation. | Provide a consistent edit lifecycle without changing Driver operations. | Driver edit and existing password/lifecycle widget tests passed. |
| `diladrive/mobile-app/lib/services/customer_profile_service.dart` | `updateMyProfile` | Omit null optional keys and serialize intentional clears. | Fix exact JSON contract and prevent accidental profile overwrites. | Original exact-body assertion and new cases passed. |
| `diladrive/mobile-app/lib/widgets/profile_edit_fields.dart` | `isMalaysianProfilePhone`, `confirmDiscardProfileChanges` | Shared mobile validation and discard dialog. | Make Driver and Customer edit behavior consistent. | Focused Flutter tests passed. |
| `diladrive/backend/diladrive-api/src/test/java/com/diladrive/CustomerTripPostgresIT.java` | Three UC003 update integration tests | Exercise real writes, protected payloads, optional preservation, corporate rules and deactivation. | Verify effects through the HTTP endpoints and PostgreSQL. | All 34 integration tests passed. |
| `diladrive/mobile-app/test/customer_profile_service_test.dart` | Exact payload test and optional-photo test | Preserved the original exact assertion. Added null omission, explicit removal, phone clear and new-image serialization. | Check the agreed contract without relaxing the assertion. | Passed. |
| `diladrive/mobile-app/test/profile_edit_contract_test.dart` | Phone tests and Driver/Customer edit widget tests | Checks reopen, dirty/no-dirty Cancel, explicit photo removal, guarded save and success. | Verify update lifecycle and protection against stale optional payloads. | Passed. |
| `diladrive/docs/admin-use-cases/UC003_Update_Account.md` | Use case flow and API contract | Updated audit to approved implementation. | Record exact behavior and remaining limitations. | Cross-checked with source. |
| `diladrive/docs/admin-use-cases/UC003_Implementation_Report.md` | Implementation and test report | Recorded changed methods, evidence and manual checks. | Provide the implementation record. | Cross-checked with commands and reports. |

## Payload and editor decisions

The original Customer serializer always emitted `profileImage: null`. The test expected only supplied keys. The corrected serializer omits null values, and the edit form sends an empty image string only after a photo removal. Optional phone and Admin address use separate clear flags. Empty or null text alone preserves data. Inputs remain trimmed in the accepted formatting rather than being rewritten to E.164.

Admin keeps the UC002 Profile read and performs an additional GET when the editor opens. Both mobile editors fetch on route entry. Driver sends its required contact fields and only includes the optional photo when changed. Customer sends required name/email, changed company/phone fields and photo changes. The mobile editors show “Profile updated.” after a successful response and keep input when a recoverable save error occurs. Cancel and system Back check for unsaved changes. During an active save they leave the screen in place.

## Authorization and regression coverage

The existing self-update endpoints are `PUT /api/auth/profile`, `PUT /api/drivers/me` and `PUT /api/customers/me`. Tests supplied protected keys including ID, role, status, verification status, password hash, vehicle ID and assignment ID. Persistence remained restricted to the session actor and allowed fields. The PostgreSQL suite also exercised missing/inactive Admin sessions, wrong mobile role and a Driver disabled after the session was established. It checked that the rejected Driver save did not change the stored name.

The full Maven suite and PostgreSQL suite cover UC001 creation/registration and booking/assignment regressions. Focused Flutter tests cover View Account, Driver password change, Driver profile refresh and the new edit flows. The full Flutter suite was also run. Booking and dashboard failures are reported below.

## Automated verification

| Command or check | Result |
|---|---|
| `npm run build -- --outDir <temporary directory>` | Passed. |
| `node --test tests/*.test.cjs` | 29 passed, zero failed or skipped. |
| Headless Chrome, post-build Admin edit with a local mock API | Passed. Five GETs and three PUTs. Verified fresh editor reads, loading, Retry, preservation of typed values/session after 503, successful response and 401 redirect. No unexpected payload keys. |
| `mvn -o -B test` | 216 passed, zero failures/errors, 5 skipped. |
| `RUN_CUSTOMER_TRIP_IT=true` with `mvn -o -B -Dtest=CustomerTripPostgresIT test` | 34 passed, zero failures/errors/skips. Used local PostgreSQL in a disposable test schema. |
| `flutter analyze --no-pub` | Passed with no issues. |
| Focused `flutter test --no-pub --reporter expanded` for profile edit, customer profile service, Driver profile enhancement/lifecycle and View Account | 19 passed, zero failed or skipped. |
| Full `flutter test --no-pub --reporter expanded` | 150 passed, 8 failed, zero skipped. The same eight booking/dashboard failures reproduced before UC003. |

Flutter tests ran in an isolated temporary copy that omitted the unused PDFium native build hook because the host lacks a Windows C compiler. Project dependencies and test assertions were preserved. The booking/dashboard failures were reproduced in the pre-UC003 copy using `booking_backend_contract_test.dart`, `booking_flow_test.dart`, `booking_location_material_test.dart`, `booking_session_expiry_test.dart` and `customer_dashboard_test.dart`.

## Manual verification and limits

- On Android, choose and remove a real gallery photo, then verify Cancel and system Back with both clean and dirty forms.
- Disable a logged-in driver from a separate admin session, then try to save on Android. The HTTP denial and no-write behavior are automated. The on-device redirect and message still need visual verification.
- Check the Admin editor layout and a real network outage in the deployed browser. The browser smoke used a mock API.
- The Customer JDK validator decodes PNG/JPEG and validates WebP still-image headers and chunk lengths. It does not fully decode WebP or accept animations with no top-level still frame.
- API callers clearing optional phone or address should send the explicit clear flag. An empty string for these fields now preserves data. Photo removal continues to use an empty string.
