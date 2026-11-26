# DilaDrive database reset

**Status:** Committed after explicit confirmation on 25 November 2026 (Malaysia). The live counts were verified after the transaction.

## Target and backup

The audited connection used database `DilaDrive` on IPv4 loopback `127.0.0.1:5432`, PostgreSQL 18.6 and the running local `postgresql-x64-18` Windows service. Its data directory is `C:/Program Files/PostgreSQL/18/data` and database OID is `24580`. Inspection used a read-only session. The database has 32 public tables and no other application table schema. The server listens on all interfaces, so loopback proves where this operation connects; it does not establish where every client connects. Stop local application writers before the reset.

Complete custom-format logical dump: `C:/Users/syafi/AppData/Local/DilaDrive/DatabaseBackups/DilaDrive-before-reset-20261125-023929.dump` (450,613 bytes). `pg_restore` read the archive to completion without writing to a database. It contains sensitive records, including password hashes; keep it outside Git. PostgreSQL server roles and filesystem uploads are outside the database dump.

The reset keeps Admin **ID 2, ADMIN, ACTIVE** and Super Admin **ID 1, SUPER_ADMIN, ACTIVE**. It preserves both rows in full, including login credentials and password hashes, as well as the existing fleet pricing settings and category-rate table. There is no separate role/permission or vehicle-category reference table in the live schema.

## Vehicle-category consequence

The current pricing selection derives categories from `vehicle.vehicle_type`. The live fleet supplies two normalized categories, Sedan and MPV. Removing every vehicle leaves that selector empty until a vehicle is registered again. The database has no independent category rows to preserve, and `booking_fare_rate` currently has no rows. The existing company pricing row remains at RM 2/km, RM 60/hour and 15% commission with the existing basis and partial-hour policy. Keeping a database-backed category selector populated with zero vehicles would require separate configuration or a design change. Neither is part of this reset.

## Affected tables

| Table | Before | Verified after reset |
| --- | ---: | ---: |
| `admin` | 2 | 2 |
| `assignment` | 1 | 0 |
| `assignment_verification_photo` | 2 | 0 |
| `booking` | 10 | 0 |
| `booking_fare_rate` | 0 | 0 |
| `booking_fare_revision` | 0 | 0 |
| `booking_pickup_alert` | 0 | 0 |
| `booking_request_idempotency` | 3 | 0 |
| `booking_vehicle_hold` | 0 | 0 |
| `customer` | 4 | 0 |
| `customer_verification_history` | 0 | 0 |
| `documents` | 7 | 0 |
| `driver` | 6 | 0 |
| `driver_assignment` | 5 | 0 |
| `driver_commission_audit` | 0 | 0 |
| `driver_trip_commission` | 1 | 0 |
| `driver_unavailable_period` | 0 | 0 |
| `invoice` | 2 | 0 |
| `mobile_push_token` | 1 | 0 |
| `notification` | 27 | 0 |
| `payment` | 2 | 0 |
| `purchase_order` | 1 | 0 |
| `quotation` | 1 | 0 |
| `receipt` | 1 | 0 |
| `trip_charge_revision` | 0 | 0 |
| `trip_incident` | 0 | 0 |
| `trip_milestone_event` | 1 | 0 |
| `trip_pricing_settings` | 1 | 1 |
| `trip_status` | 2 | 0 |
| `vehicle` | 5 | 0 |
| `vehicle_unavailable_period` | 0 | 0 |
| `verification_photo` | 2 | 0 |

The reset cleared 84 rows across 29 tables. Post-commit counts matched every value in the table above.

## Execution and verification

[Prepared SQL](../database/reset_test_data.sql) requires an explicit confirmation flag and checks the database identity, full table inventory, account IDs/roles/statuses and all expected counts. It deletes children before parents across the 53 audited foreign keys. The key paths are incident/milestone/proof → assignment → booking → vehicle/driver/customer, and receipt/document → payment → booking. It also handles the legacy quotation/purchase-order and status/photo relationships and validates deferred constraints. It compares fingerprints of retained rows in the transaction without printing credential fields. Transactional sequence restarts affected only owned sequences of emptied tables, except customer and driver account sequences. Those two keep their high-water marks to prevent previously issued session IDs from referring to new accounts. Admin and pricing sequences were preserved.

Immediately before execution, a fresh complete archive was created and read successfully by pg_restore.

- Archive: C:/Users/syafi/AppData/Local/DilaDrive/DatabaseBackups/DilaDrive-before-confirmed-reset-20261125-024735.dump
- Size: 450,613 bytes
- SHA-256: 5E558CBA9FA6546398B026E825B793882FB6178A5F0B5D64557A3EFFC3AF7AD5

The reset committed after forcing deferred constraint checks and comparing retained row fingerprints. Admin IDs 1 and 2 remained active with unchanged stored credentials. The booking sequence was verified at 1 with is_called=false. Customer and driver sequences retained their high-water marks of 8 and 21. Database checks verified that customer, driver, vehicle, scheduler, trip, payment and document data sources are empty. Pricing still shows RM 2/km, RM 60/hour and 15% commission.

A separate rollback-only SQL test inserted a pending customer and linked active driver/vehicle with explicit test IDs. Deferred foreign keys and the driver/vehicle constraint trigger passed. After rollback all three tables returned zero rows. This test covered database writes and constraints. No new application login or account API submission was performed. The local API responded to a session request with HTTP 401 without credentials. Administrator login and authenticated list, calendar, dashboard and form smoke tests remain unverified. Proof files on disk were left intact; their test database references were cleared.
