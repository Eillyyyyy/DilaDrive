# API Endpoint Map

## Implemented admin authentication and profile routes

| Method and route | Purpose |
|---|---|
| `POST /api/auth/login` | Verify admin credentials and return profile details (never the password). |
| `PUT /api/auth/profile?email={email}` | Update name, email, phone, address, and profile image. The request body must include `currentPassword`. |
| `POST /api/auth/profile/password?email={email}` | Change the password using `currentPassword` and `newPassword`. |

Profile images are sent as PNG, JPEG, or WebP data URLs and are limited to 2 MB. New and migrated passwords are stored as BCrypt hashes.

## Implemented assignment and notification routes

| Method and route | Purpose |
|---|---|
| `GET /api/assignments` | List assignments with booking, driver, and vehicle details. |
| `POST /api/assignments` | Assign a driver and vehicle to a confirmed booking; validates availability transactionally and records a driver notification. |
| `PUT /api/assignments/{id}/reassign` | Replace the assigned driver and/or vehicle and notify the new driver. |
| `PATCH /api/assignments/{id}/status` | Advance trip status; release driver and vehicle when completed or cancelled. |
| `GET /api/drivers/available` | List drivers whose availability is `AVAILABLE`. |
| `GET /api/vehicles/available` | List vehicles whose status is `AVAILABLE`. |
| `GET /api/notifications` | List notifications linked to drivers. |
| `GET /api/notifications/driver/{driverId}` | List one driver's notifications. |
| `POST /api/notifications` | Create a notification for a selected driver. |
| `PATCH /api/notifications/{id}/read` | Mark a notification as read. |
| `DELETE /api/notifications/{id}` | Delete a notification. |

Assignment creation and reassignment use `driver_assignment`, update resource status in the same database transaction, and roll back the full operation if notification persistence fails. Apply `database/schema/002_assignment_management.sql` to existing PostgreSQL databases.

## Document routes

| Method and route | Purpose |
|---|---|
| `GET /api/documents` | List documents ordered by creation time. |
| `GET /api/documents/{id}` | View one document. |
| `POST /api/documents` | Create a quotation (`documentType: QUOTATION`); customer and booking IDs must match. |
| `PUT /api/documents/{id}` | Update a draft or rejected quotation. |
| `DELETE /api/documents/{id}` | Delete a document unless a related purchase order or receipt exists. |
| `GET /api/documents/search?query={text}` | Search document number and customer name. |
| `GET /api/documents/type/{type}` | Filter by `QUOTATION`, `PURCHASE_ORDER`, `INVOICE`, or `RECEIPT`. |
| `GET /api/documents/dashboard` | Return document counts and total receipt amount from PostgreSQL. |
| `POST /api/documents/{id}/approve` | Approve a draft quotation. |
| `POST /api/documents/{id}/reject` | Reject a draft quotation. |
| `POST /api/documents/{id}/purchase-order` | Generate a purchase order from an approved quotation. |
| `POST /api/documents/invoices` | Generate an invoice for a booking. |
| `POST /api/documents/{id}/receipt` | Generate a receipt for an issued invoice after payment confirmation. |

`GET /api/documents/dashboard` runs this PostgreSQL aggregate query:

```sql
SELECT
    COUNT(*) FILTER (WHERE document_type = 'QUOTATION') AS total_quotations,
    COUNT(*) FILTER (WHERE document_type = 'PURCHASE_ORDER') AS total_purchase_orders,
    COUNT(*) FILTER (WHERE document_type = 'INVOICE') AS total_invoices,
    COUNT(*) FILTER (WHERE document_type = 'RECEIPT') AS total_receipts,
    COALESCE(
        SUM(amount) FILTER (WHERE document_type = 'RECEIPT'),
        0.00
    ) AS total_receipt_amount
FROM documents;
```

The current documents schema does not store a payment-record ID or payment status. Receipt generation therefore requires an explicit verified-payment confirmation and a payment reference; both are recorded in the receipt remarks. Apply `database/schema/003_document_management.sql` to existing PostgreSQL databases. It creates the table when absent, adds indexes, and ensures booking/customer foreign keys; it stops with an explicit error if existing documents contain orphan references.

## Other planned routes

| Module | Suggested routes |
|---|---|
| Authentication | `POST /api/auth/register`, `POST /api/auth/logout` |
| Customers | `GET/POST /api/customers`, `GET/PUT /api/customers/{id}` |
| Drivers | `GET/POST /api/drivers`, `GET/PUT /api/drivers/{id}` |
| Vehicles | `GET/POST /api/vehicles`, `GET/PUT/DELETE /api/vehicles/{id}` |
| Bookings | `GET/POST /api/bookings`, `GET/PUT /api/bookings/{id}`, `POST /api/bookings/{id}/cancel` |
| Assignments | Assignment history and deletion routes |
| Trip status | `POST /api/trip-status`, `GET /api/driver-assignments/{id}/statuses` |
| Verification photos | `POST /api/verification-photos`, `GET /api/driver-assignments/{id}/photos` |
| Payments | `POST /api/payments`, `GET /api/payments`, `PATCH /api/payments/{id}/verify` |
| Corporate documents | `/api/quotations`, `/api/purchase-orders`, `/api/invoices`, `/api/receipts` |
| Notifications | External push delivery and notification preferences |

Before deployment, protect private endpoints with JWT and role-based authorization. Validate all incoming fields and never store plaintext passwords.
