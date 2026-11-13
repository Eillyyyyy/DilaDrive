# API Endpoint Map

## Implemented admin authentication and profile routes

| Method and route | Purpose |
|---|---|
| `POST /api/auth/login` | Verify admin credentials, establish a server-side session, and return profile details (never the password). |
| `GET /api/auth/session` | Return the active administrator profile for the current session. |
| `POST /api/auth/logout` | Invalidate the current administrator session. |
| `PUT /api/auth/profile` | Update the signed-in administrator's own name, email, phone, address, and profile image. |
| `POST /api/auth/profile/password` | Change the signed-in administrator's password using `currentPassword` and `newPassword`. |

## Administrator management routes

| Method and route | Purpose |
|---|---|
| `GET /api/admins` | List administrator accounts for a signed-in admin. |
| `GET /api/admins/{id}` | Return one administrator account. |
| `POST /api/admins` | Create an administrator; SUPER_ADMIN only. |
| `PUT /api/admins/{id}` | Update an administrator; ADMIN may update only their own profile, while only SUPER_ADMIN can change roles. |

All admin web requests use the HttpOnly server session cookie. Administrator passwords are BCrypt-hashed and never included in responses. Apply `database/schema/007_admin_management.sql` to add role, status, and audit timestamps. Existing administrators default to `ADMIN`; promote the trusted initial administrator explicitly after applying the migration, for example:

```sql
UPDATE admin
SET admin_role = 'SUPER_ADMIN'
WHERE admin_email = 'trusted-bootstrap-admin@example.com';
```

The update is intentionally explicit; the migration does not grant SUPER_ADMIN to an arbitrary existing account.
For HTTPS deployments, set `SESSION_COOKIE_SECURE=true` so the session cookie is sent only over TLS.

Profile images are sent as PNG, JPEG, or WebP data URLs and are limited to 2 MB. New and migrated passwords are stored as BCrypt hashes.

## Implemented assignment and notification routes

| Method and route | Purpose |
|---|---|
| `GET /api/assignments` | List assignments with customer, booking, driver, and vehicle details from joined PostgreSQL records. |
| `GET /api/assignments/mine` | List assignments belonging to the signed-in driver session. |
| `GET /api/assignments/{id}` | Return one assignment with its related records. |
| `POST /api/assignments` | Assign an available driver and vehicle to a confirmed booking; saves the assignment and driver notification transactionally. |
| `PUT /api/assignments/{id}` | Reassign the driver and/or vehicle; omitted IDs keep the current resource. |
| `PUT /api/assignments/{id}/reassign` | Reassignment alias retained for existing clients. |
| `PUT /api/assignments/{id}/complete` | Mark the assignment and booking completed and release its driver and vehicle. |
| `DELETE /api/assignments/{id}` | Cancel an active assignment, release its driver and vehicle, and retain the row for history and dependent records. |
| `PATCH /api/assignments/{id}/status` | Advance trip status; releases resources when completed or cancelled. |
| `GET /api/drivers/available` | List drivers whose availability is `AVAILABLE`. |
| `GET /api/drivers/me` | Return the signed-in driver's own profile, including existing license and availability fields. |
| `PUT /api/drivers/me` | Update only the signed-in driver's name, email, and phone; license and account-management fields remain admin-managed. |
| `GET /api/vehicles/available` | List vehicles whose status is `AVAILABLE`. |
| `GET /api/notifications` | List notifications linked to drivers. |
| `GET /api/notifications/driver/{driverId}` | List one driver's notifications (own driver session or Admin). |
| `GET /api/notifications/customer/mine` | List notifications for the signed-in customer. |
| `POST /api/notifications` | Create a notification for a selected driver (Admin session required). |
| `PATCH /api/notifications/{id}/read` | Mark a notification as read (Admin or matching mobile recipient). |
| `DELETE /api/notifications/{id}` | Delete a notification (Admin session required). |

Customer booking submission, Admin approval, assignment changes, and driver trip-status updates create customer notifications in the same database transaction as the booking/assignment change. Customer notification list and mark-read requests are scoped to the mobile session; another customer's notification is returned as not found. Assignment creation and reassignment use `driver_assignment`, set both assigned resources to `UNAVAILABLE`, update old resources to `AVAILABLE`, and record driver notifications in the same database transaction. The assignment listing query joins `driver_assignment`, `booking`, `customer`, `driver`, and `vehicle`; `assignment_date` is returned as `assignmentDate` in the API response. Assignment deletion is a soft cancellation: the assignment row remains with `CANCELLED` status so history and dependent trip records are preserved. The availability endpoints filter the existing tables using `driver_availability = 'AVAILABLE'` and `vehicle_status = 'AVAILABLE'`. Apply `database/schema/002_assignment_management.sql` if the assignment table or driver notification relationship has not yet been created. Apply `database/schema/006_assignment_date_alignment.sql` to existing databases that still use `assigned_at`; it preserves those timestamp values when copying them into `assignment_date`.

## Customer booking routes

| Method and route | Purpose |
|---|---|
| `GET /api/bookings/mine` | List bookings owned by the signed-in customer session. |
| `GET /api/bookings/availability/recommendation` | Recommend the smallest currently available linked driver/vehicle whose recorded passenger and luggage item capacities meet the request. |
| `GET /api/bookings/availability` | Legacy availability count endpoint for signed-in admins or customers. |
| `GET /api/bookings/fare-estimate` | Report that an estimate is unavailable while no fare rules or route-distance provider are configured. |
| `POST /api/bookings/customer` | Create a pending request for an `ACTIVE` customer; customer ID comes from the session, and driver, vehicle assignment, and fare are server-controlled. Accepts optional `Idempotency-Key` for safe retries. |
| `PUT /api/bookings/{id}/customer` | Update the signed-in customer's own pending booking. The server locks the record, rechecks customer status, trip validation, and vehicle availability, preserves the pending workflow, and clears any stale fare. |
| `POST /api/bookings/{id}/cancel` | Cancel the signed-in customer's own pending or confirmed booking if it has no active assignment. |
| `PUT /api/bookings/{id}/fare` | Set a positive final fare on a pending booking (Admin session required). |

Customer booking requests remain `PENDING_VERIFICATION` until an Admin enters the final fare and confirms them. Assignment is a separate Admin action through `POST /api/assignments` after confirmation. Apply `database/schema/008_customer_booking_details.sql` to add trip type, notes, and the optional vehicle luggage-capacity field without changing existing rows. A vehicle with no recorded luggage capacity can only be recommended when the customer has no luggage.

The optional `Idempotency-Key` is scoped to the signed-in customer. Retrying the
same key and trip data returns the original booking reference; reusing it with
changed data returns HTTP 409. Apply
`database/schema/009_booking_idempotency.sql` before enabling this retry
protection on an existing database.

## Customer profile routes

| Method and route | Purpose |
|---|---|
| `GET /api/customers/me` | Return the profile for the customer in the mobile session. |
| `PUT /api/customers/me` | Update the authenticated customer's name, email, and phone; company name and registration number are also accepted for company accounts. Customer type, verification status, and password remain unchanged. |

The customer list and customer administration routes require an Admin session. Mobile profile routes require a `CUSTOMER` session and do not accept an ID from the client.

## Vehicle routes

| Method and route | Purpose |
|---|---|
| `GET /api/vehicles` | List vehicle records for fleet management. |
| `GET /api/vehicles/{id}` | View one vehicle's saved details. |
| `PUT /api/vehicles/{id}` | Update a vehicle's registration, model, type, capacity, expiry dates, and status. |
| `DELETE /api/vehicles/{id}` | Delete a vehicle unless an assignment or booking still references it. |
| `GET /api/vehicles/available` | List vehicles whose status is `AVAILABLE`. |

All `/api/vehicles` routes and `/api/dashboard/stats` require the signed-in
administrator session used by the admin web app. Vehicle records and aggregate
fleet/dashboard counts are not exposed to unauthenticated or driver sessions.

Vehicle update requests use the entity field names, for example:
`{"vehiclePlateno":"WXY 1234","vehicleModel":"Toyota Hiace","vehicleType":"MPV","vehicleCapacity":12,"vehicleRoadtaxexpiry":"2027-05-01","vehicleServiceexpiry":"2027-06-01","vehicleStatus":"AVAILABLE"}`.

`POST /api/drivers` creates a driver and their linked vehicle in one transaction. The request includes the driver fields and a nested `vehicle` object:
`{"driverName":"Ayla Izaty","driverEmail":"ayla@example.com","driverPassword":"example-password","driverPhoneno":"0123456789","driverStatus":"PENDING","driverAvailability":"AVAILABLE","vehicle":{"vehicleModel":"Toyota Hiace","vehiclePlateno":"MCA1234","vehicleType":"MPV","vehicleCapacity":7,"vehicleRoadtaxexpiry":"2027-05-01","vehicleServiceexpiry":"2027-06-01","vehicleStatus":"AVAILABLE"}}`.
If driver creation, vehicle validation, or vehicle persistence fails, both writes roll back. Apply `database/schema/005_driver_vehicle_link.sql` to existing databases to add the vehicle-to-driver foreign key.

## Document routes

| Method and route | Purpose |
|---|---|
| `GET /api/documents` | List documents ordered by creation time. |
| `GET /api/documents/customer/mine` | List quotations, invoices, purchase orders, and receipts owned by the signed-in `COMPANY` customer. |
| `GET /api/documents/{id}` | View one document. |
| `GET /api/documents/search?query={text}` | Search document number and customer name. |
| `GET /api/documents/type/{type}` | Filter by `QUOTATION`, `PURCHASE_ORDER`, `INVOICE`, or `RECEIPT`. |
| `GET /api/documents/dashboard` | Return document counts and total receipt amount from PostgreSQL. |
| `POST /api/documents/quotation` | Create a draft quotation for an existing booking. |
| `PUT /api/documents/{id}` | Update a draft or rejected quotation. |
| `PUT /api/documents/{id}/approve` | Approve a draft quotation. |
| `PUT /api/documents/{id}/reject` | Reject a draft quotation. |
| `POST /api/documents/{id}/generate-po` | Generate a purchase order from an approved quotation. |
| `POST /api/documents/{id}/generate-invoice` | Generate an invoice from a purchase order or completed booking. Request body: `{"sourceType":"PURCHASE_ORDER"}` or `{"sourceType":"BOOKING"}`. |
| `PUT /api/documents/{id}/mark-paid` | Mark an issued invoice as paid. |
| `POST /api/documents/{id}/generate-receipt` | Generate a receipt for a paid invoice. |
| `GET /api/documents/{id}/pdf?download=true` | Generate and download a PDF document. Set `download=false` to open it inline for printing. |

Document management and PDF routes require an administrator session. The customer document route derives the customer ID from the mobile session and only returns rows for that customer when `cust_type` is `COMPANY`.

Document source links are stored in `documents.source_document_id`. Apply
`database/schema/004_document_workflow.sql` to an existing database after
`003_document_management.sql`. Configure `DOCUMENT_COMPANY_NAME`,
`DOCUMENT_COMPANY_ADDRESS`, `DOCUMENT_COMPANY_EMAIL`, and
`DOCUMENT_COMPANY_PHONE` for PDF company details.

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

## Additional implemented mobile and admin routes

| Method and route | Purpose |
|---|---|
| `POST /api/auth/mobile/login` | Sign in a mobile customer or admin-created driver and establish the mobile session. |
| `POST /api/auth/mobile/register` | Register a customer account; driver self-registration is not provided. |
| `POST /api/auth/logout` | Invalidate the current session. |
| `GET /api/payments/mine` | List payments for the signed-in customer. |
| `POST /api/payments/bookings/{bookingId}` | Submit a manual payment proof as multipart fields `amount`, `paymentMethod`, and `proof`. |
| `POST /api/payments/bookings/{bookingId}/cash` | Record cash received for an assigned booking as its driver, or for a booking as an Admin. |
| `GET /api/payments` | List payments for Admin review. |
| `PUT /api/payments/{paymentId}/review` | Approve or reject a payment proof as an Admin. |
| `GET /api/payments/{paymentId}/proof` | Read payment proof for its owning customer or an Admin. |
| `GET /api/assignments/{id}/photos` | List verification photos for an assignment owned by the signed-in driver. |
| `POST /api/assignments/{id}/photos` | Upload a verification photo as multipart `photoType` and `file`. |
| `GET /api/assignments/{id}/photos/{photoId}/content` | Read photo content for the signed-in driver who owns the assignment. |
| `GET /api/drivers/me` | Return the signed-in driver's own profile. |
| `PUT /api/drivers/me` | Update the signed-in driver's name, email, and phone. |

These endpoints use the application's existing server-side session and role checks. Payment proof and cash recording are manual workflows; no online payment gateway is integrated. Verification photo routes require the signed-in driver to own the assignment. Only JPEG and PNG images up to 10 MB are accepted. Photo metadata uses the existing `verification_photo` table; image files are saved under `VERIFICATION_PHOTO_STORAGE_DIR` (default `./uploads/verification-photos`). Configure this directory on persistent storage before deployment because local container files may not survive a redeploy.

