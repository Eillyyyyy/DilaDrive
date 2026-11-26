# UC004 — Verify Account

## Use case, actors and purpose

**UC004 — Verify Account** · Update (U). An active ADMIN or SUPER_ADMIN reviews Individual (`NORMAL`) and Corporate (`COMPANY`) registrations. Customer receives the result and can read the inbox. Authentication is a precondition. The workflow is independent of Admin and Driver account creation.

## Preconditions and postconditions

The reviewer has a valid server-side admin session and a current active row with an accepted role. The customer must exist and be `PENDING`. Approval changes the status to `ACTIVE`. Rejection changes it to `REJECTED` and stores a reason. The customer write, decision history and unread inbox notice commit together. No other customer attributes are written by the decision.

## Approval main flow

1. Customer self-registers through Flutter. `AuthController.mobileRegister → MobileAuthService.registerCustomer` stores `PENDING`.
2. Admin opens Customers, selects **Pending**, opens the record and reviews name, email, phone, category and status. Corporate review includes stored company name and registration number.
3. Admin selects **Approve account** and confirms. `Customers.jsx › handleVerify` locks submission in the browser and calls the dedicated PATCH with `{"decision":"APPROVE"}`.
4. `CustomerController.verifyCustomer` resolves `adminId` from the server session. `CustomerVerificationService.verify` loads the current Admin and locks the customer via `CustomerRepository.findByIdForUpdate`. If pending, it sets ACTIVE, reviewer and decision time and writes history.
5. `NotificationService.createCustomerNotification` persists an unread “Account Approved” inbox item. HTTP 200 returns a decision receipt. React refreshes details and list and displays success.
6. Customer sees **Verified / Active** on a fresh self-profile read and can proceed through existing booking eligibility checks.

## Rejection alternative flow

Admin selects **Reject account** on a pending record, enters a nonblank reason and confirms. React trims the reason and submits `{"decision":"REJECT","reason":"..."}`. Spring validates a maximum of 500 characters, records `REJECTED` and the reason, and saves history and an unread “Account Verification Rejected” inbox notice in the transaction. The reason appears in the customer’s own Profile, View Account and inbox. The customer may still sign in and edit permitted personal fields. New bookings remain blocked. Reconsideration and resubmission are outside UC004.

## Other alternatives and exceptions

| Situation | Current behavior |
|---|---|
| Admin cancels | Dialog closes without sending a decision. |
| Invalid decision, unexpected JSON key, blank/overlong rejection reason, or reason on approval | HTTP 400. The Admin UI also validates blank and length before submission. |
| Missing/expired Admin session | HTTP 401. React follows the existing session-expiry path to Login. |
| Missing reviewer row, inactive Admin or unsupported Admin role | HTTP 403. React shows the failure and retains local session state. |
| Missing customer | HTTP 404. React refreshes the list and removes the stale selection. |
| ACTIVE, REJECTED, blank/unknown status, or losing concurrent request | HTTP 409. React refreshes the table/selection before the next decision. |
| Timeout, network failure or 5xx | React re-reads the customer list before enabling new decisions. If refresh fails, decisions remain blocked until the user refreshes. The PATCH is never silently retried. |
| Required database write fails | Transaction rolls back. Existing advice reports a translated database outage as HTTP 503 and integrity conflicts as HTTP 409. |
| Push unavailable | Existing committed inbox item remains readable. Push failure is independent of the decision. |

## Editable and protected fields

The decision request accepts only `decision` and an optional `reason` for rejection. Reviewer and timestamp are server-derived. The URL supplies only the customer target. DTO and service do not accept profile values, account category, customer status, reviewer overrides, password, vehicle or assignment data from the client. Generic `PUT /api/customers/{id}` rejects a nonnull `custStatus` with HTTP 400 and retains ordinary Admin editing when omitted. UC003 self-update still uses its own allowlisted DTO and excludes status and verification metadata.

## Authorization and transitions

Both `ADMIN` and `SUPER_ADMIN` require a current `ACTIVE` record. A stale admin cookie is insufficient if the current Admin has been deactivated. Mobile-only sessions cannot verify. The service accepts only `PENDING → ACTIVE` or `PENDING → REJECTED`. It uses a pessimistic row lock so later decisions see the committed status. The same transaction saves history and the inbox notification. Hibernate dynamic update limits unrelated customer writes to changed columns and avoids writing stale verification metadata from another profile edit.

## API and DTOs

| API | Request and response |
|---|---|
| `PATCH /api/customers/{id}/verification` | `CustomerVerificationRequest` accepts uppercase APPROVE/REJECT. Rejection needs a trimmed reason of 1–500 characters. Response `CustomerVerificationResponse` has customerId, category, status, decision, verifiedAt, reviewerId and rejectionReason where present. No password or profile data. |
| `GET /api/customers` | Existing Admin list supplies current status and registered company details. Rejection reason is excluded from raw Customer JSON. |
| `PUT /api/customers/{id}` | Existing Admin profile edit. Supplying status returns 400. |
| `GET /api/customers/me`, `PUT /api/customers/me` | Owner-only `CustomerProfileResponse` exposes rejectionReason only when the customer is rejected. The PUT cannot write it. |
| `GET /api/notifications/customer/mine`, `PATCH /api/notifications/{id}/read` | Existing session-owner inbox and read flag. Other customers cannot fetch or mark the notice via their sessions. |

## Source inventory

| Layer | Files and methods |
|---|---|
| React | `diladrive/web-admin/src/pages/Customers.jsx` · `fetchCustomers`, `askToVerify`, `handleVerify`, `StatusBadge`. `diladrive/web-admin/src/App.jsx` passes the stable session-expiry callback. `diladrive/web-admin/src/services/api.js` sends credentials. |
| Flutter | `diladrive/mobile-app/lib/models/customer_profile.dart` maps reason. `diladrive/mobile-app/lib/screens/customer/profile.dart` and `view_account.dart` render the shared status view and fetch on Check status. `create_booking.dart` distinguishes rejected status and reloads on Check again. The existing `notification.dart` displays verification notices through its regular inbox UI. |
| Spring | Under `diladrive/backend/diladrive-api/src/main/java/com/diladrive/`, `controller/CustomerController.java` routes decisions, `service/CustomerVerificationService.java` writes the transaction, `repository/CustomerRepository.java` locks, and `entity/Customer.java` maps decision fields. `service/CustomerService.java` closes the generic status bypass. The request and response DTOs are under `dto/`. |
| Notification | `NotificationService.createCustomerNotification` saves the notice and uses `BookingPushService.notifyAfterCommit`. `NotificationController` retains existing ownership checks. |
| Migration | `diladrive/database/schema/028_customer_account_verification.sql`. Apply after migration 027 using the schema README instructions. |

## Database, audit and push

Migration 028 adds `customer.rejection_reason`, `verified_at` and `verified_by_admin_id` referencing `admin.admin_id`. `customer_verification_history` stores its generated ID, customer/reviewer foreign keys, decision, previous/new status, reason, and `decided_at`. It constrains decision combinations and indexes customer/time. Existing rows keep their original statuses. Legacy metadata stays null and no backfill is manufactured.

The service inserts `notification.recipient_role = CUSTOMER`, `recipient_id = cust_id`, and type `ACCOUNT_VERIFICATION` under the same transaction. Spring registers optional FCM scheduling after successful commit. Its push body directs the customer to the app and keeps the reason in the protected inbox. FCM may be disabled, unavailable or have no token. The inbox item is still available. Actual FCM delivery is separate from inbox persistence and was not tested.

## Booking and UML

All three customer booking creation endpoints retain their persisted ACTIVE check. A PENDING or REJECTED customer receives HTTP 403 before booking persistence. Check status and Check again fetch current data without changing approval state. The Use Cases and CRUD diagram groups Admin account verification with account updates. No include or extend relationship is attached to that action.

## Test results and limitations

See [UC004 Implementation Report](UC004_Implementation_Report.md) for exact commands, counts, regression coverage and manual verification. Migration 028 was exercised in a disposable test schema. It was not applied to an application environment. Legacy rejected rows without a stored reason display the rejected status without inventing a reason.
