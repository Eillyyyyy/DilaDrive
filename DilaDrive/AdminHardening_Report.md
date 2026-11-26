# Admin hardening

The changes use the existing schema. The unavailable-period controls use the
existing driver and vehicle period tables. The reminder job uses the existing
`notification.event_key` from migration 036.

## Phase 1 test run

- `mvn -o test` reported 251 passed, 2 failed, and 9 skipped.
- The focused PostgreSQL run passed 3 tests with 0 failures and 0 skips.
- Both full-suite failures are in `VehicleCategoryCapacityTest`. Its two
  legacy-capacity assertions use caps from before the category-based capacity
  refactor. The Admin hardening work leaves vehicle matching rules intact.

## Phase 2 test run

- `mvn -o test` reported 251 passed, 2 failed, and 9 skipped. The same two
  legacy-capacity assertions failed.
- The focused PostgreSQL run passed 8 tests with 0 failures and 0 skips.
- The admin production build passed with a bundle-size warning.
- Browser interactions and the opt-in tests outside the focused runs are
  UNVERIFIED.

| Item | Change | Tests | Status |
| --- | --- | --- | --- |
| 1. Verified receipts | Require a persisted verified payment for receipt creation. Keep manual generation disabled and gate mark-paid and receipt totals on verified payments. | Mixed-status PostgreSQL test and manual receipt service test. | PASS |
| 2. Confirmation fare | Ignore the input fare. Use the stored fare or derive it from recorded distance and server pricing. Bookings missing measured distance go through the existing road-distance workflow. | HTTP binding ignores supplied fare. PostgreSQL confirmation preserves the stored fare despite a forged value. | PASS |
| 3. Last Super Admin | Serialize role removals and deactivation by locking active Super Admin rows. Only a Super Admin can change account status through the existing update API. | Self-demotion, self-deactivation, other-account access and concurrent privilege removals on PostgreSQL. | PASS |
| 4. Live admin session | Existing request interceptor reloads the authenticated admin, caches the check for that request and invalidates an inactive or missing account session. | Live session returns 401 after deactivation. | PASS |
| 5. Admin write boundaries | Retain explicit validated DTOs and remove unused legacy luggage overrides from vehicle writes. | Customer and vehicle mass-assignment tests, including multipart. | PASS |
| 6. Private files and CSRF | Reviewed in code. See findings below. | Runtime symlink, cache and CSRF checks were not run. | UNVERIFIED |
| 7. Dated unavailability | Existing API uses the driver or vehicle row lock. Add compact controls to existing driver and vehicle detail dialogs. | PostgreSQL verified access, duration rules, conflicting booking IDs, deletion, and a race with confirmation. | API PASS. Browser UNVERIFIED. |
| 8. Customer receipts | Owner-scoped listing and PDFs use verified payments for individual and corporate customers. Quotations and invoices remain corporate-only. | Both account types, rejected payments, PDF responses and cross-customer 404. | PASS |
| 9. Purchase orders | Record client POs and return 410 from the retired endpoint. Quotation edits are limited to draft and rejected records. | PostgreSQL test of both PO paths and quotation edits in all three states. | PASS |
| 10. Notifications | Persist registration, quotation approval, reassignment and trip status notifications. Deduplicate the 24-hour and 2-hour reminder windows for each recipient. | PostgreSQL workflow tests, including repeated updates and scheduler runs. Push delivery was not exercised. | Server PASS. Push UNVERIFIED. |
| 11. Driver IC | Optional on Admin create and edit. Supplied IC numbers still undergo format validation and encryption. | Multipart registration with and without IC details and database assertions for encrypted storage. | Create API PASS. Browser and optional edit submission UNVERIFIED. |
| 12. Dashboard | Align assignment subtitle with its query and use Malaysia month boundaries for verified payment totals. | Mixed-status PostgreSQL test of verified payments, pending review count, invoice balance and receipt amount. | PASS |
| 13. Rejected customers | Current behavior documented below. | Code inspection only. | UNVERIFIED |

## Private files and CSRF review

Photo, payment proof and fleet document controllers use `Cache-Control:
no-store`. The document PDF endpoints generate files from authorized database
records and omit an explicit no-store header. The private binary storage paths
perform lexical normalization and root checks. Symlink resolution or explicit
symlink rejection is absent in those read paths. Restrict write access to the
storage roots and consider real-path checks and no-store for private PDFs in a
separate change.

The session cookie defaults to `SameSite=Strict` and is configurable.
Credentialed CORS accepts explicit origins. SameSite limits cross-site cookie
delivery, while same-site sibling origins, policy changes and deployment
configuration can still expose cookie-authenticated mutations. Use explicit CSRF
tokens or validated request origins if those origins are untrusted or the
cookie policy is relaxed. Treat Strict plus tightly controlled same-site
origins as a deployment mitigation.

## Rejected customers

Rejected customers can sign in to see their account status. Booking requires
an active customer account, and registration reserves the existing normalized
email address, so the same email cannot be used to re-register.

## Driver document requirements

Admin driver registration and editing accept an omitted IC number or IC
document. An explicit IC document upload requires a file. New accounts still
require a driving licence and road-tax document, and document uploads require
the configured fleet encryption key.
