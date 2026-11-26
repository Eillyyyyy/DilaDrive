# DilaDrive use cases

The four external actors are Super Admin, Admin, Customer, and Driver. Authentication is a prerequisite for protected actions. Availability, price calculation, and notification dispatch support actor goals.

| ID | Use case | Actors |
| --- | --- | --- |
| UC001 | Create Account | Customer, Admin, Super Admin |
| UC002 | View Account | All roles |
| UC003 | Update Account | All roles |
| UC004 | Verify Account | Admin |
| UC005 | Create Vehicle | Admin |
| UC006 | View Vehicle | Admin |
| UC007 | Update Vehicle | Admin |
| UC008 | Create Booking | Customer |
| UC009 | View Booking | Admin, Customer |
| UC010 | Update Booking | Customer |
| UC011 | Cancel Booking | Customer, Admin |
| UC012 | Verify Booking | Admin |
| UC013 | Assign Driver and Vehicle | Admin |
| UC014 | Reassign Trip | Admin |
| UC015 | View Trip | Admin, Customer, Driver |
| UC016 | Update Trip Status | Admin, Driver |
| UC017 | Upload Proof of Service | Driver |
| UC018 | Report Incident | Driver |
| UC019 | Generate Quotation | Admin |
| UC020 | View Quotation | Admin, Customer |
| UC021 | Update Quotation | Admin |
| UC022 | Record Purchase Order | Admin |
| UC023 | Generate Invoice | Admin |
| UC024 | View Invoice | Admin, Customer |
| UC025 | Generate Receipt | Admin |
| UC026 | View Receipt | Admin. Customer access deferred |
| UC027 | Record Payment | Admin, Driver. Customer access deferred |
| UC028 | View Payment | Admin. Customer access deferred |
| UC029 | Update Payment | Admin |
| UC030 | View Pricing | Admin |
| UC031 | Update Pricing | Admin |
| UC033 | View Notification | Admin, Customer, Driver |

The Admin account role retains Super Admin inheritance in the diagram. Create Vehicle follows the existing coupled driver-and-vehicle registration workflow. Record Purchase Order records the reference from a client after quotation approval. The previous generation route is retained for compatibility. View Trip includes actor-specific access to current status and recorded history. Report Incident is a separate driver goal. Corporate enforcement and invoice timing are covered in the business rules.

## Temporary Customer scope

Customer use cases retain account, booking, trip, corporate quotation/invoice and notifications. Customer receipt and payment actions are deferred. Existing use case IDs and non-Customer workflows are retained. See [Customer booking workflow](CUSTOMER_BOOKING_WORKFLOW.md).
