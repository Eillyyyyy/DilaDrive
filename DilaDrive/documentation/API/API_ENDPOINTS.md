# Proposed API Endpoint Map

All routes below are planned endpoints; only `GET /api/health` exists in the starter backend.

| Module | Suggested routes |
|---|---|
| Authentication | `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/logout` |
| Customers | `GET/POST /api/customers`, `GET/PUT /api/customers/{id}` |
| Drivers | `GET/POST /api/drivers`, `GET/PUT /api/drivers/{id}` |
| Vehicles | `GET/POST /api/vehicles`, `GET/PUT/DELETE /api/vehicles/{id}` |
| Bookings | `GET/POST /api/bookings`, `GET/PUT /api/bookings/{id}`, `POST /api/bookings/{id}/cancel` |
| Assignments | `POST /api/driver-assignments`, `PUT /api/driver-assignments/{id}` |
| Trip status | `POST /api/trip-status`, `GET /api/driver-assignments/{id}/statuses` |
| Verification photos | `POST /api/verification-photos`, `GET /api/driver-assignments/{id}/photos` |
| Payments | `POST /api/payments`, `GET /api/payments`, `PATCH /api/payments/{id}/verify` |
| Corporate documents | `/api/quotations`, `/api/purchase-orders`, `/api/invoices`, `/api/receipts` |
| Notifications | `GET /api/notifications`, `PATCH /api/notifications/{id}/read` |

Before deployment, protect private endpoints with JWT and role-based authorization. Validate all incoming fields and never store plaintext passwords.
