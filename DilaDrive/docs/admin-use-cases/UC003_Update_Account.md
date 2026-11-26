# UC003 — Update Account

## Business rules

- Admin, Driver and Customer update their own permitted profile fields. Spring Boot resolves identity from the server session. Administrator edits of another account remain separate.
- Opening an editor fetches fresh account data and shows a loading state. Failed reads offer Retry. Driver and Customer can discard unsaved changes through Cancel or Back.
- Unchanged optional fields are omitted. Explicit removal uses the controls described below.
- COMPANY customers must retain a nonblank company name and registration number. The persisted category controls which fields are accepted. A PENDING customer can update their details without gaining booking access.
- Driver account activation and Vehicle assignments stay server-controlled. UC002 rejects a deactivated Driver session before the profile PUT. Admin role and status, customer verification, and password hashes remain outside the profile update DTOs.

## Actors, preconditions and postconditions

The actors are an authenticated active Admin, an authenticated active Driver, and an authenticated Customer of either category. The corresponding account must exist. On success, PostgreSQL contains the updated permitted details, the frontend shows the returned profile and a success message, and protected fields keep their values. No schema change is required.

## Main flows

| Actor and step | File / method | Request and result |
|---|---|---|
| Admin opens Profile, then Edit Profile | `diladrive/web-admin/src/App.jsx` · `loadEditProfile`; `diladrive/web-admin/src/pages/Profile.jsx` · `openEditor` | UC002 Profile fetch stays in place. Edit triggers its own credentialed `GET /api/auth/session`. The form opens only after this response. A cancelled request cannot populate the form. |
| Admin saves | `Profile.jsx` · `handleSubmit`; `App.jsx` · `handleSaveProfile`; `diladrive/web-admin/src/services/api.js` | `PUT /api/auth/profile` sends name and email plus changed optional fields. The updated response refreshes screen state and local storage. |
| Admin server write | `AuthController.updateProfile → AdminSession.requireAdminId → AdminService.updateProfile(Long) → AdminRepository.findById/save` | Reads and writes `admin`. The actor must still be active. Returns HTTP 200 and `AdminProfileResponse`. |
| Driver opens Update account | `diladrive/mobile-app/lib/screens/driver/profile.dart` · `_editProfile`; `diladrive/mobile-app/lib/screens/driver/update_account.dart` · `initState`, `_loadProfile` | `DriverProfileService.getProfile` fetches `GET /api/drivers/me`. The fresh response populates the form. |
| Driver saves | `driver/update_account.dart` · `_save`; `diladrive/mobile-app/lib/services/driver_profile_service.dart` · `updateProfile` | `PUT /api/drivers/me` sends required contact fields. The photo is sent only if selected or removed. Returns to Profile with the updated model and “Profile updated.” |
| Driver server write | `DriverSessionGuard.preHandle → DriverController.updateMyProfile → DriverService.updateProfile → DriverRepository.findByIdForUpdate/save` | Locks and writes the session driver's `driver` row. Existing vehicle and trip-summary reads still enrich `DriverProfileResponse`. |
| Customer opens Update account | `diladrive/mobile-app/lib/screens/customer/profile.dart` · `_editProfile`; `diladrive/mobile-app/lib/screens/customer/update_account.dart` · `initState`, `_load` | `CustomerProfileService.getMyProfile` calls `GET /api/customers/me`. The editor loads once per opening and does not use the previously displayed snapshot as form data. |
| Customer saves | `customer/update_account.dart` · `_save`; `diladrive/mobile-app/lib/services/customer_profile_service.dart` · `updateMyProfile` | `PUT /api/customers/me` sends required name/email and changed optional values. Successful response replaces the parent Profile data and shows “Profile updated.” |
| Customer server write | `CustomerController.updateMyProfile → CustomerService.updateCustomerProfile → CustomerRepository.findById/save` | Reads and writes `customer`. Validates against the stored category and checks the effective corporate fields before writing. Returns HTTP 200 and `CustomerProfileResponse`. |

Server paths are under `diladrive/backend/diladrive-api/src/main/java/com/diladrive/`, with HTTP handlers in `controller/`, authorization in `security/`, business rules in `service/`, data access in `repository/`, models in `entity/` and request/response types in `dto/`. Exact changed paths and methods are in the implementation report.

## Editable and protected fields

| Actor | Editable fields | Protected fields |
|---|---|---|
| Admin | Name, email, optional phone, address and photo. | ID, role, account status, password or hash. |
| Driver | Name, email, required phone and photo. | ID, identity card, licence and expiry, Driver type, activation, availability, vehicle, assignment and trip IDs, password or hash. |
| Customer | Name, email, optional phone and photo. COMPANY also edits name and registration number of the company. | ID, type, verification status, password or hash. NORMAL cannot submit corporate fields. |

The writes continue to use `ProfileUpdateRequest`, `DriverProfileUpdateRequest` and `CustomerProfileUpdateRequest` and explicit service setters. Extra protected JSON keys do not persist. Responses omit credentials. Existing Admin and Driver password-change endpoints remain separate. The target-ID admin endpoints for customers, drivers and admins retain their own permissions and workflows.

## Optional fields and validation

| Input | Effect |
|---|---|
| Optional phone or Admin address omitted, null or blank | Preserve the stored value. |
| Valid phone or address supplied | Store the trimmed value. Phone accepts Malaysian numbers starting with local `0`, `+60` or `60`, with spaces or hyphens. Formatting is retained after trimming. Driver phone is required. |
| `clearPhone: true` (Admin/Customer) or `clearAddress: true` (Admin) | Explicitly clear the corresponding column. The UI sends the flag from the relevant Clear control. |
| Photo omitted or null | Preserve the image. |
| `profileImage: ""` | Explicit removal. The editors only emit it after a removal action on an existing photo. |
| Valid new photo | Replace the image. Admin retains its existing 2 MiB limit. Mobile retains 900 KiB. Customer checks decoded PNG/JPEG image content and WebP container/frame structure in addition to the size and MIME checks. |
| Corporate field omitted or null | Preserve the existing value. Both effective COMPANY fields must be nonblank. An incomplete legacy COMPANY profile must supply any missing value before saving. |
| Corporate field blank, or corporate values submitted by NORMAL | HTTP 400 for blank COMPANY values, HTTP 403 for NORMAL submissions containing nonnull company values. |

The backend validates email, checks duplicate email in its account table, requires name and email, and uses the existing size limits. Phone validation does not enforce phone uniqueness. Admin and Customer optional phone validation applies when a new phone is supplied. Customer's edit page uses the UC002 safe avatar and handles picker/read failures without replacing the current photo. WebP is validated structurally because the JDK does not include a full WebP decoder.

### Original Flutter failure

`diladrive/mobile-app/test/customer_profile_service_test.dart` expected only submitted keys. `CustomerProfileService.updateMyProfile` previously serialized `profileImage: null` unconditionally, although the server treated null as preservation. The serializer now omits null optional values, so the original exact-map assertion passes. The edit form independently compares against the freshly loaded image and sends an empty string only to remove a photo. Unit and HTTP tests cover omission, explicit null, replacement and removal.

## Alternative and exception flows

- **Cancel or Back.** Admin restores its last editor snapshot. Driver and Customer exit immediately if unchanged. If dirty, the discard dialog offers Keep editing and Discard. Mobile navigation and duplicate submissions are blocked while saving.
- **Temporary load or save failure.** Edit GET offers Retry. A failed PUT keeps the form input and displays an error. Admin retains its session on network errors and server failures. The edit GET and PUT use a 15-second request timeout.
- **Expired or unauthorized session.** Admin 401 on load/save clears the local session and returns to Login. Mobile 401 follows existing session behavior. A wrong mobile role gets 403. A disabled Driver session receives 403 `DRIVER_ACCOUNT_DEACTIVATED` from UC002 and is cleared by the shared mobile handler. The pending edit is not persisted if that request is rejected.
- **Validation, duplicate data or account missing.** The API returns the existing 400/409 validation or email conflict response, or an authorization/not-found response. The frontend shows the error without success feedback. Invalid corporate input and malformed Customer images are rejected before setters run.

## Include / Extend

No include or extend relationship identified for UC003 in the existing Use Cases and CRUD diagram. Authentication is a precondition.

## Tests and remaining limits

The React build, 29 Node tests, mocked Admin Chrome smoke test and 34 PostgreSQL integration tests passed. Maven reported 216 passed and 5 skipped. Flutter analysis and all 19 focused tests passed, including the original serializer assertion. The full Flutter suite reported 150 passed and eight booking/dashboard failures, all reproduced before UC003. The Android gallery and deactivation navigation still require on-device verification. See the [UC003 Implementation Report](UC003_Implementation_Report.md) for the exact commands and limitations.
