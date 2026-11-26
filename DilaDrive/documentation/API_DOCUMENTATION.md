# DilaDrive API

The REST API uses its existing Admin and mobile sessions. Booking date/time values follow Malaysia wall time. New milestone and incident occurrence timestamps are ISO-8601 offset datetimes. Unauthorized sessions return 401/403 and protected records not owned by the caller return 404. Invalid input returns 400. Invalid milestone transitions and booking conflicts return 409.

| Method and path | Access | Request / response |
| --- | --- | --- |
| GET /api/assignments/{id}/timeline | Admin or assigned Driver | Chronological list of milestone and occurredAt |
| GET /api/bookings/{id}/timeline | Booking owner | Chronological events across the booking's allocation history |
| POST /api/assignments/{id}/incidents | Assigned Driver | JSON description, 1–2000 characters. 201 with incidentId |
| GET /api/assignments/incidents | Admin | Up to 100 recent incidents, including driver display name and assignment/booking IDs |
| GET /api/dashboard/operations | Admin | Pending review and trip counts, verified payments for the Malaysia month, issued-invoice balance and current trip milestones |
| GET /api/notifications | Admin | Current admin's notifications, newest first |
| GET /api/notifications/inbox | Admin | Zero-based `page`, `size`, `status` (ALL, UNREAD, READ), `type`, `search`. Returns `items`, `total` and `types` |
| GET /api/notifications/unread-count | Admin | Own unread `count` |
| PATCH /api/notifications/{id}/read | Notification recipient | Marks one notification read and preserves its original `readAt` on repeat requests |
| PATCH /api/notifications/read-all | Admin | Marks the current admin's inbox read and returns the remaining unread `count` |
| GET /api/notifications/sent/driver | Admin | Paginated, read-only driver notification history |
| POST /api/notifications | Admin | Sends a driver notification through the existing workflow |
| POST /api/documents/{id}/record-po | Admin | JSON clientReference, 1–100 characters. 201 with the recorded PO linked to its approved quotation |

The calendar consumes GET /api/bookings and GET /api/assignments. The existing PATCH /api/assignments/{id}/status records the timestamp in the same transaction as the transition. Duplicate current status requests return the saved result. The existing payment, pricing, account, document and booking API families are preserved. See [API endpoint inventory](API/API_ENDPOINTS.md) and [use case traceability](USE_CASE_TRACEABILITY.md).

Actionable notifications use `entityType`, `entityId` and a validated
`targetRoute`. Supported targets open the existing detail views for customers,
bookings, drivers, vehicles and assignments. Legacy messages can have a null
target. The admin portal polls every 30 seconds while visible and refreshes
when the browser tab regains focus.
