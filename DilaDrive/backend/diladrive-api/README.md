# DilaDrive Spring Boot API

Run with Java 17+, Maven, and PostgreSQL. From this directory in PowerShell:

```powershell
.\run-local.ps1
```

The local launcher uses `postgres` for PostgreSQL. To launch with a custom
password, set `DATABASE_PASSWORD` and run `./run-local.ps1 -UseConfiguredDatabasePassword`.
Direct Maven runs use the password from the environment or the development default.

Driver account creation requires `DRIVER_DATA_MASTER_KEY`. The local and development
profiles can load it from the ignored `driver-data.env.local` file in this backend
directory. Keep the same key across restarts. For a database with existing encrypted
fleet records, use its original key.

With no active profile, `development` allows credentialed HTTP requests from
`localhost` and `127.0.0.1` on any port (Flutter Web and React Admin). The `local`
profile uses the same origin policy. Production must set
`SPRING_PROFILES_ACTIVE=production` and `CORS_ALLOWED_ORIGINS` to the exact HTTPS
frontend origins, comma-separated; never use `*` in production. Explicit profiles
do not load the development origin patterns. Restart the API after configuration
changes. The central MVC configuration handles preflight before controller session
checks; this project has no Spring Security filter chain.

Flutter Web defaults to `http://localhost:8080`; override with
`flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080`.
Android emulators retain `http://10.0.2.2:8080`. React Admin uses
`http://localhost:8080/api` unless `VITE_API_BASE_URL` is set.

The React Admin web app and Flutter mobile app both call this Spring Boot API;
the API uses one PostgreSQL datasource for all clients. Configure deployments
with environment variables rather than editing `application.properties`:

| Environment variable | Purpose | Default |
| --- | --- | --- |
| `SERVER_ADDRESS` | Network interface for the API | `0.0.0.0` |
| `SERVER_PORT` | HTTP port | `8080` |
| `DATABASE_URL` | Full JDBC URL; overrides the host/port/name components | Derived from the values below |
| `DATABASE_HOST` | PostgreSQL host | `localhost` |
| `DATABASE_PORT` | PostgreSQL port | `5432` |
| `DATABASE_NAME` | PostgreSQL database name | `DilaDrive` |
| `DATABASE_USERNAME` | PostgreSQL user | `postgres` |
| `DATABASE_PASSWORD` | PostgreSQL password | `postgres` in local/development. Required in deployment. |
| `JPA_DDL_AUTO` | Hibernate schema mode | `validate` |
| `JPA_SHOW_SQL` | Log generated SQL | `true` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins; exact origins in production | Development/local: loopback on any port; other profiles: `http://localhost:5173` |
| `SESSION_COOKIE_SAME_SITE` | Session-cookie SameSite policy | `strict` |
| `SESSION_COOKIE_SECURE` | Set Secure on session cookies | `false` |

Use a unique strong `DATABASE_PASSWORD` in deployments, set
`JPA_DDL_AUTO=validate`, disable SQL logging with `JPA_SHOW_SQL=false`, set
`SESSION_COOKIE_SECURE=true` when serving over HTTPS, and configure
`CORS_ALLOWED_ORIGINS` to the exact web-app origin(s). The API binds to all
interfaces by default so physical devices can connect over a trusted network;
restrict port 8080 with host/cloud firewall rules. Do not expose a development
HTTP API or its database port to the public internet.

## PostgreSQL schema setup

This project does not include Flyway or another automatic SQL migration runner.
The scripts in `database/schema/` are manual. For a newly created empty
PostgreSQL database, run `database/schema/BASELINE_CURRENT.sql` once. It matches
the current JPA entities and includes the existing feature tables. Do not run
the numbered feature migrations after that baseline.

The default database name contains uppercase letters. PostgreSQL folds
unquoted SQL identifiers to lowercase, so create it with quotes unless you
configure `DATABASE_NAME` to match an existing database:
```powershell
psql -h localhost -p 5432 -U postgres -d postgres -c 'CREATE DATABASE "DilaDrive";'
```

From `backend/diladrive-api/`, apply the clean-install schema with:
```powershell
psql -h localhost -p 5432 -U postgres -d DilaDrive -v ON_ERROR_STOP=1 -f ../../database/schema/BASELINE_CURRENT.sql
```

`001_diladrive_schema.sql` is an obsolete starter draft and does not match the
current JPA entity columns. For example, it defines
`customer.customer_id` and a `booking.scheduled_at` column, while the current
application expects `customer.cust_id` and `booking.booking_tripdate` plus
`booking.booking_pickuptime`. Do not apply that draft.

For an existing database, do not run the clean-install baseline. Review each
forward migration's prerequisites against the actual schema and apply only the
missing migration. The scripts do not reset business records. Back up the
database before schema changes.

The idempotency table stores `created_at` as `TIMESTAMPTZ`, mapped to Java
`Instant`, so creation times retain an unambiguous UTC instant across server
timezone settings. Earlier copies of migration 009 already used this database
type.

The default `JPA_DDL_AUTO=validate` checks that the database matches the
application entities without changing the schema. Provision a new database
from the reviewed baseline and apply forward migrations explicitly before
starting the API. Set `JPA_DDL_AUTO=update` only when you intentionally want
Hibernate to modify a development schema. The SQL scripts do not reset
booking, customer, driver, or assignment records.

The web app accepts `VITE_API_BASE_URL` (defaults to
`http://localhost:8080/api`). The Flutter app accepts the compile-time Dart
define `API_BASE_URL`, for example
`flutter run --dart-define=API_BASE_URL=http://192.168.1.20:8080/api` for a
physical Android device or an HTTPS URL for a release deployment.

The mobile app authenticates existing customer and driver accounts with:
```http
POST http://localhost:8080/api/auth/mobile/login
Content-Type: application/json

{"email":"user@example.com","password":"...","role":"CUSTOMER"}
```

Use `"DRIVER"` for driver accounts. Passwords created through the customer and
driver APIs are stored as BCrypt hashes; legacy plain-text passwords are upgraded
to BCrypt after a successful login.

The following is the Admin-only `POST /api/bookings` request. It creates a
pending booking and accepts the Admin-managed fare. Customer mobile requests
use `POST /api/bookings/customer`; the customer ID and initial status come from
the session and the server, and no fare or assignment is accepted from the app:
```http
POST http://localhost:8080/api/bookings
Content-Type: application/json

{
  "custId": 1,
  "bookingTripdate": "2026-11-20",
  "bookingPickuptime": "09:30:00",
  "bookingPickupaddress": "Pickup address",
  "bookingDestinationaddress": "Destination address",
  "bookingType": "Sedan",
  "bookingTripfare": 80.00
}
```

The mobile app requests a vehicle recommendation through
`GET /api/bookings/availability/recommendation`, using locations, schedule,
passenger count, and luggage count. `GET /api/bookings/availability` is a legacy
count endpoint. An Admin sets a positive fare with `PUT /api/bookings/{id}/fare`
and confirms the pending booking with `PUT /api/bookings/{id}/verify`. A
confirmed booking is assigned separately through `POST /api/assignments`, which
checks and locks the selected driver and vehicle before saving the assignment.
Driver and vehicle availability changes are committed with that assignment.

## Branded business document PDFs

`GET /api/documents/{id}/pdf?download=true` downloads a branded PDF.
Set `download=false` to return it inline for browser preview and printing.
The PDF uses A4 pages and repeats the DilaDrive header, watermark, generated-by
footer, and page number across multi-page documents.

Company details and document terms can be configured with these environment
variables (the website and contact details are shown as not configured until
values are supplied):

| Environment variable | Purpose |
| --- | --- |
| `DOCUMENT_COMPANY_NAME` | Company name in the PDF header |
| `DOCUMENT_COMPANY_ADDRESS` | Company address |
| `DOCUMENT_COMPANY_EMAIL` | Company email |
| `DOCUMENT_COMPANY_PHONE` | Company phone |
| `DOCUMENT_COMPANY_WEBSITE` | Company website |
| `DOCUMENT_QUOTATION_TERMS` | Quotation terms and conditions |
| `DOCUMENT_QUOTATION_VALIDITY_DAYS` | Validity period from quotation date (default: 30) |
| `DOCUMENT_INVOICE_DUE_DAYS` | Invoice payment period from issue date (default: 30) |

Customer address, payment method, and payment reference are not stored by the
current application, so the PDF explicitly labels those values as not provided
or not recorded rather than inventing them.

## Confirm & Assign returns 404

See [the verified routing investigation](../../docs/confirm-and-assign-404.md).
The action is POST /api/bookings/{id}/confirm-and-assign; restart Spring Boot
after introducing a new controller, and log in again after restart. The report
distinguishes an unmapped endpoint from booking/driver/vehicle lookup failures
and includes the exact project restart command and actual test results.

### Firebase Cloud Messaging (Android)

The Android app is registered with Firebase project `diladrive-c2808`. Its
`google-services.json` is the client configuration. The backend uses Application
Default Credentials for the same Firebase project.

From this directory in PowerShell:

```powershell
.\run-local.ps1 -EnablePush -FirebaseCredentials 'C:\path\to\diladrive-service-account.json'
```

The launcher validates the service account project and keeps the credential path
in the backend process environment. Keep the service-account JSON outside the
repository. For other deployments set `BOOKING_PUSH_ENABLED=true`,
`FIREBASE_PROJECT_ID=diladrive-c2808`, and configure Application Default
Credentials with FCM send permission.

After starting the backend, sign in on Android and grant notification permission.
An admin notification addressed to that driver or a booking event for the signed-in
recipient exercises the send path. The foreground uses an in-app banner and
Android displays background notifications in the DilaDrive updates channel.
Stored notifications are available in the inbox independently of push delivery.
