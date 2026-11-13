# PostgreSQL schema scripts

These SQL files are manual scripts. This repository has no Flyway or other
migration runner.

## New empty database

For a newly created, empty PostgreSQL database, run `BASELINE_CURRENT.sql` once.
It defines the tables and columns used by the current JPA entities, including
customer bookings, manual payment proofs, assignments, business documents,
notifications, booking retry keys, and verification photos. It contains no
seed data. Do not run the
numbered feature migrations after this baseline; those changes are already
included.

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
