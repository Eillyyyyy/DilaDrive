# UC002 — View Account

## 1–6. Use case, actors and rules

**UC002 — View Account** · Read (R). Admin, Driver and Customer each view their own account. Admin opens Profile directly. Driver and Customer open View Account from the Profile menu. Each opening makes a fresh request. The account ID comes from the server session.

**Preconditions.** The actor has a valid server session and a matching account. Admin and Driver must be active. A `PENDING` Customer can view their own account. Only an approved Customer can create a booking.

**Postconditions.** The app displays the fresh account response. The read does not change profile data. A deactivated Driver is denied access and returned to Login.

## 7–9. Actual account flows

### Admin

| Step | File and method | Request or result |
|---|---|---|
| 1. Select Profile | `web-admin/src/App.jsx` · `App`, `renderPage` and the `profileOpen` effect | Shows loading and sends one credentialed GET via `services/api.js`. |
| 2. Resolve session and retrieve record | `AuthController.getSession` → `AdminSession.requireAdminId` → `AdminService.getProfile(Long)` | `GET /api/auth/session`. `AdminRepository.findById` reads `admin` by `admin_id`. |
| 3. Display account | `AdminProfileResponse` → `web-admin/src/pages/Profile.jsx` · `Profile` | HTTP 200, latest admin DTO. Retry triggers another GET. A superseded request is cancelled. |

### Driver

| Step | File and method | Request or result |
|---|---|---|
| 1. Open Profile and choose View Account | `mobile-app/lib/screens/driver/profile.dart` · `_view` → `view_account.dart` · `initState` | The Profile summary may have been loaded earlier. The detail route always starts one new load. |
| 2. Authorize and retrieve | `DriverProfileController.load` → `DriverProfileService.getProfile` → `DriverSessionGuard.preHandle` → `DriverController.getMyProfile` | `GET /api/drivers/me`. On a valid active session, `DriverService.getProfile` loads the driver row and gathers linked vehicle and summary counts. |
| 3. Display account | `DriverProfileResponse` → `DriverProfile.fromJson` → `DriverViewAccountPage.build` | HTTP 200, read-only information. Pull to refresh and Retry remain available. An in-flight load suppresses overlapping requests in the same controller. |

### Customer

| Step | File and method | Request or result |
|---|---|---|
| 1. Open Profile and choose View account | `mobile-app/lib/screens/customer/profile.dart` · `build` → `view_account.dart` · `initState` | The parent supplies its existing service. The new route starts its own GET rather than using the summary model. |
| 2. Authorize and retrieve | `CustomerProfileService.getMyProfile` → `CustomerController.getMyProfile` → `CustomerService.getCustomerProfile` | `GET /api/customers/me`. `requireCustomerId` checks the CUSTOMER role and session ID. `CustomerRepository.findById` reads `customer` by `cust_id`. |
| 3. Display account | `CustomerProfileResponse.from` → `CustomerProfile.fromJson` → `ViewAccountPage.build` | HTTP 200 even when customer status is `PENDING`. Corporate fields appear for `COMPANY`. Loading, retry, empty and sign-in states are handled by the detail route. |

## 10–11. Alternative and exception flows

- **Back without changes.** Admin can switch sidebar pages. Driver and Customer can use Back to return to Profile. No write is sent.
- **Temporary server or database failure.** The backend maps translated database failures to **503**. Admin retains their session and sees Retry on Profile. Driver and Customer services surface the request error and the detail views offer Retry.
- **Account missing.** Admin's session lookup and Customer's profile lookup return **404**. The view displays a distinct error. A missing Driver row in an existing Driver session follows the deactivation response.
- **Session missing or expired.** The self-profile APIs return **401**. Admin clears local auth and opens Login. The mobile profile services clear their local session and show a Sign in action. Wrong mobile roles receive **403**.
- **Network connection fails.** Admin shows a connection message and Retry. Flutter handles a client error or 15-second timeout and allows retry. These failures do not trigger driver deactivation.
- **Disabled Driver.** The next protected request returns **403** and `DRIVER_ACCOUNT_DEACTIVATED`. The server removes only Driver mobile identity attributes. Flutter clears the matching local session and replaces the route stack with Login, showing “Your driver account has been deactivated. Please contact the administrator.”
- **Malformed Customer image.** `CustomerProfileAvatar` catches invalid base64 and uses an image error fallback for decoded bytes that are not a valid image. The remaining account details still render.

## 12. Include / Extend Relationships

No include or extend relationship identified for UC002. The existing Use Cases and CRUD diagram associates each role with its own profile display. Authentication is a precondition.

## 13. Frontend and Mobile File Inventory

Paths are relative to the workspace root.

| Actor | Layer | Exact File Path | Function / Method | Purpose |
|---|---|---|---|---|
| Admin | React navigation | `diladrive/web-admin/src/App.jsx` | App, renderPage, profileOpen effect and handleLogin | Routes Profile and obtains session details. |
| Admin | React view | `diladrive/web-admin/src/pages/Profile.jsx` | Profile, getProfile, ProfileField | Renders own profile from props. |
| Admin | API service | `diladrive/web-admin/src/services/api.js` | Axios instance, response interceptor | Cookie credentials and API base URL. |
| Admin | API error reporting | `diladrive/web-admin/src/services/apiErrors.js` | logApiError | Logs selected request metadata. |
| Driver | Flutter navigation | `diladrive/mobile-app/lib/screens/driver/driver_navigation.dart` | build | Displays Profile tab. |
| Driver | Flutter profile | `diladrive/mobile-app/lib/screens/driver/profile.dart` | initState, build, _view | Fetches profile and opens detail view. |
| Driver | Flutter account view | `diladrive/mobile-app/lib/screens/driver/view_account.dart` | DriverViewAccountPage, initState, build | Displays personal account data. |
| Driver | API service | `diladrive/mobile-app/lib/services/driver_profile_service.dart` | getProfile, _request | Reads current profile and handles response errors. |
| Driver | Response model | `diladrive/mobile-app/lib/models/driver_profile.dart` | DriverProfile.fromJson | Decodes the API response. |
| Customer | Flutter navigation | `diladrive/mobile-app/lib/screens/customer/customer_navigation.dart` | build | Displays Profile tab. |
| Customer | Flutter profile | `diladrive/mobile-app/lib/screens/customer/profile.dart` | initState, build, _reload | Fetches profile and opens detail view. |
| Customer | Flutter account view | `diladrive/mobile-app/lib/screens/customer/view_account.dart` | ViewAccountPage.initState, _retry, build, CustomerProfileAvatar | Fetches fresh data, renders read states and handles malformed images. |
| Customer | API service | `diladrive/mobile-app/lib/services/customer_profile_service.dart` | getMyProfile, _request | Reads current profile and handles response errors. |
| Customer | Response model | `diladrive/mobile-app/lib/models/customer_profile.dart` | CustomerProfile.fromJson | Decodes the API response. |
| Driver | Controller and UI | `diladrive/mobile-app/lib/controllers/driver_profile_controller.dart` | DriverProfileController.load | Manages load, results and error state. |
| Driver | Widgets | `diladrive/mobile-app/lib/widgets/driver_profile_widgets.dart` | DriverProfileMenu, DriverProfileContent, DriverAssignedVehicleCard | Renders actions, account fields and assigned vehicle. |
| Driver / Customer | Session | `diladrive/mobile-app/lib/services/mobile_session.dart` | headers, isAuthenticated, storeResponseCookies, clear, revokeDriverSession | Manages mobile session state. |
| Driver / Customer | Auth service | `diladrive/mobile-app/lib/services/auth_service.dart` | AuthService.login | Stores login session from the server response. |
| Driver / Customer | HTTP client | `diladrive/mobile-app/lib/services/api_http_client.dart` | createApiHttpClient | Wraps the platform client to handle a structured disabled-driver response. |
| Driver / Customer | Browser HTTP | `diladrive/mobile-app/lib/services/api_http_client_web.dart` | createApiHttpClient | Uses BrowserClient with credentials. |
| Driver / Customer | API URL | `diladrive/mobile-app/lib/config/app_config.dart` | AppConfig.apiBaseUrl | Resolves the backend base URL. |

## 14. Backend File Inventory

| Actor | Layer | Exact File Path | Function / Method | Purpose |
|---|---|---|---|---|
| Admin | Controller | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/controller/AuthController.java` | getSession, requireAdminId | Resolves the actor session and invokes the service. |
| Admin | Service | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/AdminService.java` | getProfile(Long), findAdmin(Long) | Reads the authenticated account and maps the response. |
| Admin | Repository | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/repository/AdminRepository.java` | findById (inherited) | Loads the account by ID. |
| Admin | Entity | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/entity/Admin.java` | Admin | Maps the PostgreSQL account row. |
| Admin | DTO | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/dto/AdminProfileResponse.java` | AdminProfileResponse constructor | Defines the public account fields. |
| Driver | Controller | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/controller/DriverController.java` | getMyProfile, requireDriverId | Resolves the actor session and invokes the service. |
| Driver | Service | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/DriverService.java` | getProfile, profileResponse | Reads the authenticated account and maps the response. |
| Driver | Repository | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/repository/DriverRepository.java` | findById (inherited) | Loads the account by ID. |
| Driver | Entity | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/entity/Driver.java` | Driver | Maps the PostgreSQL account row. |
| Driver | DTO | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/dto/DriverProfileResponse.java` | DriverProfileResponse.from | Defines the public account fields. |
| Customer | Controller | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/controller/CustomerController.java` | getMyProfile, requireCustomerId | Resolves the actor session and invokes the service. |
| Customer | Service | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/CustomerService.java` | getCustomerProfile | Reads the authenticated account and maps the response. |
| Customer | Repository | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/repository/CustomerRepository.java` | findById (inherited) | Loads the account by ID. |
| Customer | Entity | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/entity/Customer.java` | Customer | Maps the PostgreSQL account row. |
| Customer | DTO | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/dto/CustomerProfileResponse.java` | CustomerProfileResponse.from | Defines the public account fields. |
| Driver | Authorization registration | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/config/DriverSessionConfig.java` | driverSessionInterceptor | Registers the check for API requests. |
| Driver | Authorization guard | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/security/DriverSessionGuard.java` | preHandle | Reads live driver status before the handler. |
| Admin | Session helper | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/security/AdminSession.java` | requireAdminId | Validates server-side admin session ID. |
| Driver / Customer | Session establishment | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/MobileAuthService.java` | login | Authenticates and supplies the role and ID used in AuthController.mobileLogin. |
| Driver | Vehicle service | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/service/VehicleService.java` | getAssignedVehicle | Reads vehicle by driver ID. |
| Driver | Vehicle repository | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/repository/VehicleRepository.java` | findByDriverId | Looks up linked vehicle. |
| Driver | Trip repository | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/repository/AssignmentRepository.java` | countByDriverDriverId, countByDriverDriverIdAndAssignmentStatusIgnoreCase, countUpcomingDriverTrips | Counts trips for the session driver. |
| Driver | Related entities | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/entity/Vehicle.java` | Vehicle | Maps the assigned vehicle. |
| Driver | Related entities | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/entity/Assignment.java` | Assignment | Maps the driver assignment and booking link. |
| Driver | Related entities | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/entity/Booking.java` | Booking | Provides trip date and time for upcoming counts. |
| All | Exception mapping | `diladrive/backend/diladrive-api/src/main/java/com/diladrive/controller/ApiExceptionHandler.java` | handleDatabaseUnavailable, handleResponseStatus, handleDataIntegrity | Maps translated database and business errors. |
| All | Schema | `diladrive/database/schema/BASELINE_CURRENT.sql` | admin, driver, customer, vehicle, driver_assignment, booking | Defines the account and linked tables. |

## 15–17. API, PostgreSQL and data fields

| Actor | Endpoint | PostgreSQL | Fields displayed |
|---|---|---|---|
| Admin | `GET /api/auth/session` | `admin` | Picture or initials, name, email, phone, address and role. |
| Driver | `GET /api/drivers/me` | `driver`, `vehicle`, `driver_assignment`, `booking` | Picture, name, email, phone, licence number and expiry, driver type, availability and account status. The response still carries assigned vehicle details and trip counts for existing consumers. |
| Customer | `GET /api/customers/me` | `customer` | Picture, name, email, phone, type and status. Corporate adds company name and registration number. |

Requests use session credentials and carry no account ID or body. The three DTOs exclude passwords and password hashes. The Driver DTO also excludes the identity-card number.

## 18. Authorization

`DriverSessionConfig.driverSessionInterceptor` maps `DriverSessionGuard` to `/api/**`. It excludes sign-in, sign-out and registration endpoints and skips CORS preflight. Existing ownership and role checks remain in the controllers. The guard reads `driver_status` via `DriverRepository.findById` on each request with a Driver mobile session and allows only `ACTIVE`. Driver availability remains an operational field.

`createApiHttpClient` recognizes the structured deactivation response across the shared mobile transport. `MobileSession.revokeDriverSession` checks the request's session generation so a delayed response cannot revoke a newer login. `main.dart` returns to Login and displays the message. Other 403 responses and temporary failures retain their existing handling.

## 19. Test results and remaining limitations

The React build and mocked Chrome smoke test passed. Maven reported 216 passed and 5 skipped, with zero failures. All 31 PostgreSQL integration tests passed. Flutter analysis found no issues. In the isolated Flutter test run, 31 tests passed, including both new UC002 tests. One existing Update Account test also failed on the original code because the request includes `profileImage: null`.

The status change is enforced when the driver next calls a protected endpoint. Driver profile retrieval still uses its existing linked vehicle and trip summary queries. The Android device flow requires manual verification. See [UC002 Implementation Report](UC002_Implementation_Report.md) for test commands, limitations and exact changed files.
