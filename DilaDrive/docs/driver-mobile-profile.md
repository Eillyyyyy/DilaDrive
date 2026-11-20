# Driver mobile profile enhancement

Implemented 17 November 2026 in the existing Flutter/Spring Boot modules.

## Behavior

Driver-facing profile screens no longer render Driver ID or database identifiers. Existing internal model identity fields remain for compatibility with the current navigation/session/notification contracts; no ID is editable or included in owned profile/password update bodies.

Driver Profile now follows the Customer Profile UI: a gradient identity header and individual navigation cards. All dashboard/statistics cards have been removed. Account information shows the photo, read-only driver fields and six assigned-vehicle fields using Customer-style field cards. Missing optional values have explicit fallbacks. Missing summary fields from an older backend display Unavailable rather than invented zero counts.

Update Account permits only name, email, phone and photo changes. Gallery selection, replacement, preview, removal, file-type/900 KB validation and in-flight state handling are implemented. Password changes use a separate validated form, current-password verification and BCrypt hashing. Administrator-managed information is displayed read-only. Existing Help, About, Logout, View/Update navigation and the parent onDriverUpdated callback are preserved.

The project did not have Provider/BLoC dependencies. A reusable SDK ChangeNotifier controller is used with AnimatedBuilder, preserving the existing StatefulWidget form architecture without changing application dependencies. Async lifecycle guards prevent late responses from updating disposed state.

## Additive backend contract

Existing routes and fields remain supported:

- GET /api/drivers/me: retains existing fields and adds profileImage, vehicleModel, vehiclePlate, totalTrips, completedTrips and upcomingTrips.
- PUT /api/drivers/me: retains name/email/phone and accepts optional profileImage. Omission preserves the existing picture; an empty string removes it. PNG/JPEG/WebP data URIs are validated and limited to 900 KB decoded size.
- POST /api/drivers/me/password: new session-scoped endpoint accepting currentPassword and newPassword; returns 204 on success. Confirm New Password is validated by the Flutter form. New passwords require at least 8 characters and at most 72 UTF-8 bytes. Incorrect current passwords return 403; invalid new passwords return 400.

The server always derives the driver from the authenticated DRIVER session. It does not accept caller-selected ownership. License, type, status, availability and vehicle relationships are not modified by the owned profile endpoint. Customer verification and booking/assignment business rules were not changed.

Trip counts use PostgreSQL-backed assignment repository queries. Total includes the driver's assignment records, Completed counts COMPLETED, and Upcoming counts ASSIGNED trips whose pickup is not in the past, evaluated in Asia/Kuala_Lumpur. The vehicle comes from the existing current-driver relationship.

## Database

`database/schema/021_driver_profile_image.sql` was applied to the local database. It adds one nullable TEXT column, driver.driver_profile_image. No existing profile values or relationships are rewritten. Other environments must apply 021 before deploying the updated backend. New server-side fields are additive for older Flutter/API clients.

## Files for this enhancement

Flutter:

- mobile-app/lib/models/driver_profile.dart
- mobile-app/lib/services/driver_profile_service.dart
- mobile-app/lib/controllers/driver_profile_controller.dart (new)
- mobile-app/lib/widgets/driver_profile_widgets.dart (new)
- mobile-app/lib/screens/driver/profile.dart
- mobile-app/lib/screens/driver/view_account.dart
- mobile-app/lib/screens/driver/update_account.dart
- mobile-app/test/driver_profile_enhancement_test.dart (new)

Backend (under backend/diladrive-api/src/main/java/com/diladrive):

- entity/Driver.java
- dto/DriverProfileResponse.java
- dto/DriverProfileUpdateRequest.java
- repository/AssignmentRepository.java
- service/DriverService.java
- controller/DriverController.java

Database and checks:

- database/schema/021_driver_profile_image.sql
- database/schema/README.md
- backend/diladrive-api/src/test/java/com/diladrive/service/DriverServiceTest.java
- backend/diladrive-api/src/test/java/com/diladrive/controller/DriverControllerTest.java
- backend/diladrive-api/src/test/java/com/diladrive/DriverProfilePostgresIT.java (new)
- docs/driver-mobile-profile.md

Unrelated existing workspace edits, including booking, notification, dependency and IDE changes, were preserved.

## Validation actually executed

- Flutter analyze --no-pub: **No issues found**.
- Full Spring Boot unit/MVC suite plus existing-data read-only PostgreSQL check: **164 passed**.
- DriverProfilePostgresIT: **3 passed**, exercising owned profile image/contact persistence, protected admin fields, vehicle/summary response fields, password change and subsequent login, invalid images and cross-role rejection. Tests create test-owned paired records and roll back; existing accounts are not edited.
- Follow-up DriverServiceTest, DriverControllerTest and DriverProfilePostgresIT after password-boundary validation: **23 passed** (rechecks, not additional unique tests).
- Isolated Flutter checks against actual source files: **6 passed**, covering model compatibility, photo/password request bodies and session cookies, backend error messages, controller disposal, live/read-only profile cards without IDs, and invalid-image avatar fallback.
- Full Flutter test execution was attempted but stopped before tests ran because the existing pdfium_dart native build hook requires a Windows C++ compiler not configured on this machine. A temporary minimal Flutter test harness avoided that unrelated PDF build dependency for the six focused checks; it did not change application package dependencies.

The new full-screen regression test file is present and statically analyzed. Its full execution, native gallery interaction and physical-device behavior remain unverified until the Windows toolchain/device environment is available. No APK or device build is claimed.

The Flutter SDK/Pub cache requires access beyond the workspace sandbox, so analysis and focused tests used approved SDK access. Maven used the installed Java 25 runtime with the Byte Buddy compatibility flag and forkCount=0. The SQL migration requires backend restart to load the new entity field; the API does not auto-run manual schema scripts.

## Relationship display follow-up

Driver Update now uses the reusable DriverAssignedVehicleCard for six read-only fields: model, plate, type, passenger capacity, road tax expiry and operational status. The owned profile response adds vehicleType, vehicleCapacity, vehicleRoadTaxExpiry, vehicleStatus and an explicit vehicleAssigned flag; existing response fields remain intact. Missing relationships display No assigned vehicle.

Vehicle Edit shows driver name, email, phone, type and status in a read-only description list using the already fetched driver API records. Those properties are excluded from the vehicle save payload. Missing relationships display No assigned driver. Vehicle editing and existing assignment safeguards are unchanged. This follow-up requires no database migration.

Additional files: mobile-app/test/driver_vehicle_relationship_test.dart and updates to web-admin/src/pages/Vehicles.jsx, web-admin/tests/vehicle-readonly.test.cjs, the shared Flutter profile widgets/model/update screen, DriverProfileResponse.java, DriverServiceTest.java and DriverProfilePostgresIT.java. Validation: 18 web tests, 30 targeted backend checks, 3 isolated Flutter model/widget tests passed; React production build and Flutter analysis passed. The broader Flutter PDF/compiler limitation documented above remains unchanged.


## Driver Account cleanup and mapping audit (17 November 2026)

DriverViewAccountPage now passes showOverview: false to the shared content widget, removing its avatar/header card and all four statistics cards. It renders only Driver Information and Assigned Vehicle. The separate main Profile page keeps its existing overview and navigation.

The verified ownership path is mobile DRIVER login -> MobileLoginResponse.id = Driver.driverId -> server session mobileUserId/mobileRole -> GET /api/drivers/me -> requireDriverId -> DriverService.getProfile -> VehicleService.getAssignedVehicle -> VehicleRepository.findByDriverId -> DTO -> Flutter DriverProfile. No caller-supplied driver/vehicle ID is needed or trusted. Vehicle ID is not assumed to equal driver ID, and operational status does not hide the vehicle.

Read-only PostgreSQL/API checks confirmed current links 1 -> vehicle 1, 2 -> vehicle 2, 3 -> vehicle 7 and 5 -> vehicle 3. Driver 4 genuinely has no matching vehicle.driver_id row. This proves the current backend mapping works, including unequal IDs. The particular driver's active browser/device session was not supplied, so the audit does not claim which account the user was viewing. No links were automatically created or reassigned.

A frontend ambiguity was fixed: omitted vehicle metadata from an older/incomplete API response previously fell through to No assigned vehicle. DriverProfile.vehicleAssignmentKnown now distinguishes unknown data from an explicit backend vehicleAssigned: false. Unknown data displays an unavailable/refresh message, while No assigned vehicle is shown only for the backend's explicit no-match result. Existing older responses that actually include vehicle data still display it. API/network errors remain errors with retry, rather than becoming empty relationships.

No authentication, business rules, production backend implementation or schema changes were needed. The six read-only vehicle fields are model, plate, type, passenger capacity, road tax expiry and status.

Files changed for this follow-up:

- mobile-app/lib/screens/driver/view_account.dart
- mobile-app/lib/widgets/driver_profile_widgets.dart
- mobile-app/lib/models/driver_profile.dart
- mobile-app/test/driver_profile_enhancement_test.dart
- mobile-app/test/driver_vehicle_relationship_test.dart
- mobile-app/test/driver_account_content_test.dart
- backend/diladrive-api/src/test/java/com/diladrive/DriverVehicleReadOnlyPostgresIT.java
- docs/driver-mobile-profile.md

Checks actually executed: dart format completed; Flutter analyze of all six changed Dart files found no issues. Targeted Maven compilation/tests passed 28/28 (DriverVehicleReadOnlyPostgresIT 2, DriverProfilePostgresIT 3, DriverServiceTest 15, MobileAuthServiceTest 8). The read-only API audit compares all six vehicle DTO values with the database for every existing driver and verifies a caller-supplied different driverId cannot override the session identity. Profile write fixtures roll back and existing business rows remain unchanged.

Full targeted Flutter tests were attempted but blocked before execution by the existing pdfium_dart native build hook: this Windows host has no configured C++ compiler. Five focused model/widget tests passed in a temporary minimal Flutter harness that exports the actual production model/widgets/theme, without altering application dependencies. They cover a 390-pixel phone account layout with exactly two cards/no statistics, all six read-only vehicle values including a MAINTENANCE vehicle, explicit missing assignment, stale optional fields and unknown API metadata. The temporary harness was removed after verification. Full-screen navigation/device testing remains unverified; the full-screen regression was updated and statically analyzed.


## Customer-style Driver Profile redesign (latest)

The design reference was the actual customer/profile.dart, customer/view_account.dart and customer/update_account.dart. Customer screens were read but not modified. Driver Profile now uses the same 18/16/18/28 list padding, navy gradient header with 22-pixel radius/padding and a 68-pixel avatar, 19-pixel bold name, and individual menu Cards with circular icons, 10-pixel bottom margins and chevrons. The menu is View account, Update account, Vehicle information, Change password and Sign out. Sign out uses the same Stay/Sign out confirmation flow as Customer Profile.

All Total/Completed/Upcoming Trip and Assigned Vehicle summary widgets, their builder and the showOverview switch were removed from the driver UI. Legacy summary fields remain in the API/model for compatibility; they are not rendered.

Account information has a 96-pixel photo/avatar and Customer-style individual value cards (16-pixel radius, zero elevation, 17/15 padding, secondary 13-pixel labels, bold values, 10-pixel spacing). It displays all requested driver fields and an Assigned Vehicle section. Vehicle information is another menu destination using the existing account screen in vehicle-only mode, fetching fresh data from the same owned API. Its six values remain read-only.

Update account now uses the Customer form structure: centered photo chooser, plain white rounded inputs separated by 14 pixels and a 52-pixel FilledButton. Existing image validation, preview/replacement/removal, contact validation, loading states and actual API errors were retained. Saving returns the updated profile to the parent and refreshes MobileUser identity. Administrator fields are available in View Account, never editable in the update form.

Change password is a dedicated screen accessible from both the Profile menu and Update account. It reuses the existing POST /api/drivers/me/password contract and current/new/confirm validation, including the existing minimum length and 72-byte limit. Successful changes return to the previous screen; authentication rules were not changed.

Vehicle loading still uses the server-authenticated DRIVER session, not widget.driver.id or an editable route/query ID. The API/database verification was rerun: both DriverVehicleReadOnlyPostgresIT tests passed, including all six DTO fields for every current account and unequal driver/vehicle IDs. Unknown/incomplete API vehicle data remains distinct from the explicit no-match state. No backend production code, relationships, schema or business records were changed for this redesign.

Files changed in this redesign:

- mobile-app/lib/screens/driver/profile.dart
- mobile-app/lib/screens/driver/view_account.dart
- mobile-app/lib/screens/driver/update_account.dart
- mobile-app/lib/screens/driver/change_password.dart (new)
- mobile-app/lib/widgets/driver_profile_widgets.dart
- mobile-app/test/driver_profile_enhancement_test.dart
- mobile-app/test/driver_account_content_test.dart
- docs/driver-mobile-profile.md

Verification actually executed:

- dart format: completed.
- Flutter analyze on all seven changed Dart files: no issues.
- Real application flutter build web --no-pub: passed, including the real login routes and unchanged dependencies.
- Focused Flutter tests: 14 passed. Tests cover model compatibility, existing API bodies/cookies, disposal, phone-width account layout, six read-only vehicle fields, explicit missing vs unknown vehicle information, the five-menu navigation, vehicle-only route, sign-out cancellation, password validation, successful password save/return, and successful contact save updating the parent profile.
- The focused Flutter run used a temporary test harness containing the production Driver Profile source and dependencies, with only the unrelated LoginPage destination stubbed to avoid transitive PDF native hooks. It did not change application dependencies. The harness was removed after testing. This is not a full application/Android end-to-end test.
- Maven compilation and current PostgreSQL/API checks: 2 passed, no failures. Only read-only tests were run for this redesign; no application records were edited.

Remaining verification: native gallery selection on an Android device, interactive device/browser visual comparison and a full native application test run were not performed. The existing PDF package's missing Windows C++ toolchain remains a limitation for the standard Windows test command. No pixel-perfect screenshot comparison or APK build is claimed. Hot-restart Flutter to load the redesigned screens; this change requires no backend restart or database migration.
