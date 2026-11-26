# DilaDrive use case traceability

| ID | Use case | Runtime path | Data |
| --- | --- | --- | --- |
| UC001 | Create Account | AuthController, DriverController, AdminController | customer, driver, vehicle, admin |
| UC002 | View Account | Role-specific profile and Admin account APIs | customer, driver, admin |
| UC003 | Update Account | CustomerController, DriverController, AdminController | customer, driver, admin |
| UC004 | Verify Account | PATCH /api/customers/{id}/verification | customer, customer_verification_history, notification |
| UC005 | Create Vehicle | POST /api/drivers with a linked vehicle | vehicle, driver |
| UC006 | View Vehicle | GET /api/vehicles/{id} and /api/assignments/schedule | vehicle, driver, driver_assignment, booking_vehicle_hold, vehicle_unavailable_period |
| UC007 | Update Vehicle | PUT /api/vehicles/{id} | vehicle, driver |
| UC008 | Create Booking | POST /api/bookings/availability, /estimate and /customer | booking, booking_vehicle_hold, booking_request_idempotency, vehicle, driver, notification |
| UC009 | View Booking | GET /api/bookings and /api/bookings/mine | booking, driver_assignment, booking_vehicle_hold |
| UC010 | Update Booking | PUT /api/bookings/{id}/customer/complete | booking, booking_vehicle_hold, booking_fare_revision |
| UC011 | Cancel Booking | POST /api/bookings/{id}/cancel; existing Admin assignment cancellation | booking, booking_vehicle_hold, driver_assignment, notification |
| UC012 | Verify Booking | POST /api/bookings/{id}/confirm-and-assign or /reject | booking, driver_assignment, booking_vehicle_hold, notification |
| UC013 | Assign Driver and Vehicle | Booking confirmation and existing legacy assignment repair | driver_assignment, booking, vehicle, driver, trip_milestone_event |
| UC014 | Reassign Trip | PUT /api/assignments/{id}/reassign | driver_assignment, trip_milestone_event, booking, driver, vehicle, notification |
| UC015 | View Trip | GET /api/assignments, /mine and owned timeline routes | driver_assignment, booking, trip_milestone_event |
| UC016 | Update Trip Status | PATCH /api/assignments/{id}/status | driver_assignment, trip_milestone_event, booking, notification |
| UC017 | Upload Proof of Service | POST /api/assignments/{id}/photos | assignment_verification_photo, driver_assignment |
| UC018 | Report Incident | POST /api/assignments/{id}/incidents | trip_incident, driver_assignment, notification |
| UC019 | Generate Quotation | POST /api/documents/quotation | documents, booking, customer |
| UC020 | View Quotation | Document listing and PDF endpoints | documents, booking, customer |
| UC021 | Update Quotation | PUT /api/documents/{id} | documents |
| UC022 | Record Purchase Order | POST /api/documents/{id}/record-po | documents, booking, customer |
| UC023 | Generate Invoice | POST /api/documents/{id}/generate-invoice | documents, booking, trip_charge_revision |
| UC024 | View Invoice | Document listing and PDF endpoints | documents, booking |
| UC025 | Generate Receipt | POST /api/documents/{id}/generate-receipt; verified payment flow | documents, payment, booking |
| UC026 | View Receipt | Existing document listing and PDF endpoints. Deferred in Customer navigation | documents, payment |
| UC027 | Record Payment | Existing POST /api/payments/bookings/{id} or /cash as permitted. Customer action deferred | payment, booking, notification |
| UC028 | View Payment | Existing GET /api/payments and /mine. Customer action deferred | payment, booking |
| UC029 | Update Payment | PUT /api/payments/{id}/review | payment, documents, notification |
| UC030 | View Pricing | GET /api/admin/pricing and /trip-pricing-settings | booking_fare_rate, trip_pricing_settings |
| UC031 | Update Pricing | Admin pricing mutation endpoints | booking_fare_rate, trip_pricing_settings |
| UC033 | View Notification | GET /api/notifications and owned inbox routes | notification, mobile_push_token |

Supporting UI paths include Admin Bookings and Calendar for booking and allocation, Assignments for Admin milestones, Flutter My Bookings for the customer timeline, Driver Trips for incidents, Admin Documents and Flutter Business Documents for PDFs. The test report records which checks were executed. The implemented ERD includes legacy relations still present in PostgreSQL.

[Temporary Customer workflow](CUSTOMER_BOOKING_WORKFLOW.md) describes the active booking-to-completion scope.
