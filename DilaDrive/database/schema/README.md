# PostgreSQL schema scripts

These SQL files are manual scripts. This repository has no Flyway or other
migration runner.

## New empty database

For a newly created, empty PostgreSQL database, run `BASELINE_CURRENT.sql` once.
It defines the tables and columns used by the current JPA entities, including
customer bookings, manual payment proofs, assignments, business documents,
notifications, booking retry keys, and verification photos. It contains no
seed data. Do not run the
older numbered feature migrations after this baseline; those changes are already
included. For Option A, also apply `020_one_driver_one_vehicle.sql` and `021_driver_profile_image.sql`, which was
introduced after this baseline snapshot.

The baseline uses ordinary `CREATE TABLE` statements and fails if its tables
already exist. This prevents it from silently replacing or adapting an
existing schema.

`001_diladrive_schema.sql` is an obsolete starter draft, not the current
application baseline. It uses columns such as `customer.customer_id` and
`booking.scheduled_at`; the current JPA entities use `customer.cust_id` and
`booking.booking_tripdate`/`booking.booking_pickuptime`. Do not run it.

For an existing database, do not run `BASELINE_CURRENT.sql`. Inspect each
forward migration's prerequisites and apply only the missing migration after
reviewing it against that database. Back up the database before schema
maintenance. The numbered migrations are intended to preserve existing
business rows; they do not provide a database reset procedure. The API's local
`JPA_DDL_AUTO=validate` is the application default. Provision the matching
schema from this reviewed baseline and the required forward migrations before
starting the API. Use `JPA_DDL_AUTO=update` only when you intentionally want
Hibernate to modify a development schema.

Migration `015_account_email_uniqueness.sql` adds case-insensitive unique
indexes for customer, driver, and admin login emails. It checks for duplicate
nonblank addresses first and aborts without changing rows if any require
review. Resolve those records before retrying the migration; do not delete
accounts automatically.

Migration `016_booking_address_length.sql` widens booking pickup and
destination address columns to 500 characters to match the API validation
limit. It aborts without changing rows if an existing address exceeds that
limit; review such records before retrying it.

Migration `017_notification_message_length.sql` widens notification message
storage to 2,000 characters to match the Admin notification request limit. It
also aborts without changing rows if a legacy message exceeds that limit.

Migration `018_manual_payment_proofs.sql` upgrades the legacy payment table in
place, keeps its original method/status/date values in `legacy_payment_*`
columns, maps the old pending/paid and bank-transfer labels to the current
workflow, and adds proof uploads, staff-recorded cash, and linked receipts. It
does not delete bookings, payment rows, or existing documents. Verified legacy
payments without a matching receipt get a linked receipt record.

`booking_request_idempotency.created_at` uses `TIMESTAMPTZ`, mapped to Java
`Instant`. This keeps idempotency record creation times unambiguous regardless
of the database or application server timezone.

Option A requires `020_one_driver_one_vehicle.sql` before starting the updated API.
It adds a unique current driver link, requires every vehicle to reference a driver,
enforces paired creation with a deferred constraint trigger, and adds a nullable
reservation end time for existing history. It also enforces normalized plate and
driver email uniqueness. It does not update or delete business records. It aborts
if orphan vehicles or duplicate driver links/normalized identifiers need review.
Existing drivers without vehicles are preserved and can be repaired explicitly
from Driver Management; no vehicle is assigned to them automatically. Old account
statuses and vehicle status values are not rewritten. New driver accounts are ACTIVE.

`021_driver_profile_image.sql` adds nullable TEXT storage for driver profile photos.
It preserves existing rows and does not change vehicle links, account status or
customer verification. Apply it before deploying the driver profile enhancement.


Apply `022_active_booking_assignment.sql` after 020/021 for atomic Booking confirmation.
It checks for duplicate active assignments and adds a unique partial index per booking.
It never creates assignments or changes legacy booking statuses. Review any duplicates
before retrying; do not delete history automatically.

Apply `025_distance_pricing.sql` after `023_customer_booking_flow.sql` and
`024_pending_without_reservation.sql` before deploying distance-based pricing.
It extends the existing `booking_fare_rate` table with an ID and timestamps,
validates categories against the fleet, and adds `booking_fare_revision` history.
It never seeds rates or deletes company records. Admin must enter approved MYR
rates in Settings → Pricing; missing or inactive pricing is a 422 business error,
while physical availability remains independent of pricing. Existing accepted
booking snapshots are preserved when company rates change.

### Trip pricing and actual charges

Apply `026_trip_charges_and_commission.sql` after the current baseline and
required forward migrations, including `025_distance_pricing.sql` when setting
up the road-pricing flow. This migration is additive. It leaves stored booking
fares, payment records, issued invoices and assignments intact. Deploy the API
after applying it, since JPA validates the new booking and vehicle columns.

The migration provisions RM2/km and RM60 per approved hour, with a common
distance rate and prorated partial hours by default.
Administrators can enable the existing category-specific per-kilometre rates.
Hourly charges are applied only to duration explicitly approved by an Admin.
Actual toll and parking are entered separately. Existing accepted booking fares
remain fixed until the existing explicit fare-review workflow changes them.
Legacy bookings without an hourly snapshot take the current policy on the first
actual charge approval or eligible completion. Commission tables and columns from this historical
migration are retained for existing records. The application no longer reads
or writes them.

Vehicle-specific large luggage and backpack limits are optional during upgrade
so legacy combined luggage limits continue to work. Configure both limits for
each vehicle to enforce each dimension independently. Known vehicle categories
also cap the recorded seats at 3, 5, 8, 11 and 15 respectively. A single
passenger can still use a Sedan.

The current charge revision is a replacement of the total approved amounts,
and a repeated save of the same current amounts does not add another charge.
When an invoice already exists, an approved revision records the invoice ID and
the difference from its issued amount. The issued PDF and its payable amount
are preserved. This ledger does not automatically collect, refund, or issue
credit notes for the difference.

A commission is recorded once, when an eligible assignment reaches COMPLETED
through the backend workflow. Its month is based on the time of completion in
Asia/Kuala_Lumpur. The upgrade does not infer historical completion timestamps
or create backdated commissions. Admins may record payment status after actual
settlement. Reopening and recalculating commission records creates an audit
entry. Customer payment records are independent of driver commission.

Apply `027_admin_audit_relationships.sql` after the pricing migration.
It validates the links from payment verification, cash recording and fare
proposals to Admin accounts. It reuses existing equivalent validated foreign
keys and rolls back if any existing reference is invalid.

Apply `028_customer_account_verification.sql` after 027 and before starting an
API build containing UC004. For an existing installation, use the configured
database and schema for that installation. From the repository root, after
checking the target and taking your normal backup, run:

```powershell
psql -X -v ON_ERROR_STOP=1 -d <configured-database> -f diladrive/database/schema/028_customer_account_verification.sql
```

For a new database, run the baseline and its required forward migrations first.
Migration 028 adds nullable customer decision metadata, foreign keys, a history
table and an index. It leaves existing statuses and records in place and does
not fabricate reviewer identities, reasons or timestamps for legacy rows.


Apply "029_booking_flight_schedule.sql" after migration 028 before running the updated backend against an existing database. It adds nullable flight date and flight time columns to booking. Existing rows need no backfill.

Apply `030_booking_assignment_preference.sql` after 029 before starting the updated backend. It adds nullable booking preference and reassignment history fields and a persistent pre-pickup alert marker. Existing bookings have no exact preferred vehicle until a customer submits or edits a complete booking.

## Scheduled booking reservations

Apply 031_pending_pair_reservations.sql to upgraded databases after the booking and assignment migrations. It reuses booking_vehicle_hold and preserves vehicle-only legacy holds. New holds record both resources, expire in Malaysia local time, and are ignored after expiry. Clean installations include the hold table in BASELINE_CURRENT.sql.

### Trip monitoring

Apply `032_trip_milestones_incidents.sql` after migration 031 before starting the refinement backend. It adds two tables and a lookup index. Existing assignments are retained without fabricated timestamps. The current baseline already includes the new tables.

### Driver identity and fleet documents

Apply `033_driver_identity_documents.sql` to existing databases before deploying
the protected driver identity and fleet document workflow. The migration adds
encrypted IC fields, vehicle categories, private document metadata and access
audit history without deleting existing driver or vehicle records. Configure
`DRIVER_DATA_MASTER_KEY` as a Base64-encoded 32-byte secret before starting the
API if legacy IC numbers need to be encrypted or new documents uploaded.

Apply `034_fleet_expiry_reminders.sql` after migration 033. It adds persistent
deduplication records for driver licence, vehicle road-tax and vehicle service
expiry alerts. New databases include both changes in `BASELINE_CURRENT.sql`.
The daily scheduler sends configured reminders to active Admin accounts in
Asia/Kuala_Lumpur. Replacing a document stores the new encrypted document and
its audit metadata; the superseded encrypted file is removed after the
transaction commits.

Apply `035_vehicle_boot_capacity.sql` after migration 034 before starting the
category-based boot capacity backend on an existing database. New databases
already have this schema in `BASELINE_CURRENT.sql`. The migration links
recognized legacy vehicle types to their categories and backfills the stored
vehicle capacities. Vehicles with an unrecognized type keep their stored
capacities until an administrator assigns a category. The legacy vehicle
luggage column remains available for older integrations and stores boot units.

Apply `036_actionable_notifications.sql` before deploying the actionable
admin inbox on an existing database. It adds optional record targets, read
timestamps and an event deduplication key. Existing messages and their read
states remain available. New databases include these fields in the baseline.
