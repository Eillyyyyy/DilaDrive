# DilaDrive

Integrated Transport Management System for Fadilah Transportation.

| Folder | What | Stack |
|--------|------|-------|
| `backend/` | REST API | Java 17+, Spring Boot 3, Spring Data JPA, PostgreSQL, server sessions |
| `web-admin/` | Admin dashboard | React + Vite |
| `mobile-app/` | Customer + driver app (one app, role-based) | Flutter (Android) |
| `backend/diladrive-api/` | Shared REST API | Spring Boot + PostgreSQL |

Design decisions live in `docs/DILADRIVE_DECISIONS_AND_ERD_V2.md` (it overrides parts of the blueprint).

## Quick start

### 1. Database
Create the PostgreSQL database named exactly `DilaDrive` (quoted because the
name contains uppercase letters), or configure `DATABASE_NAME` to match your
existing database. From a PostgreSQL installation, create the default name with:
```powershell
psql -h localhost -p 5432 -U postgres -d postgres -c 'CREATE DATABASE "DilaDrive";'
```
The backend defaults to `localhost:5432`, database user `postgres`, and the
local development password configured by `DATABASE_PASSWORD`. Override these
values with the backend environment variables in
`backend/diladrive-api/README.md`.
For a new empty database, apply `database/schema/BASELINE_CURRENT.sql` once.
Do not use it on an existing database; see the database setup notes before
applying schema changes.
From the repository root, the clean-install command is:
```powershell
psql -h localhost -p 5432 -U postgres -d DilaDrive -v ON_ERROR_STOP=1 -f database/schema/BASELINE_CURRENT.sql
```

### 2. Backend
```powershell
cd backend/diladrive-api
.\run-local.ps1
```
The local launcher uses `postgres` as the database password. For a custom
password, set `DATABASE_PASSWORD` and run the launcher with
`-UseConfiguredDatabasePassword`.
- The API listens on `0.0.0.0:8080` by default so LAN-connected devices can reach it.
- Restrict network access to the API port with host/cloud firewall rules.
- Swagger UI: http://localhost:8080/swagger-ui.html

### 3. Admin web
```bash
cd web-admin
npm install
npm run dev
```
The default API base is `http://localhost:8080/api`. Set `VITE_API_BASE_URL`
when the web app and API are deployed at different addresses.

### 4. Mobile
See `mobile-app/README.md` for emulator, physical-device, and release API URLs.

For Chrome development, use the VS Code launch configuration **DilaDrive Chrome - API 8080** or run:

```powershell
cd mobile-app
flutter run -d chrome
```

The local API allows `http://127.0.0.1:5174` for credentialed requests.
Admin web uses port 5173. Flutter reads its fixed port from
`mobile-app/web_dev_config.yaml`.

Web Admin and Flutter Mobile use the same backend API and PostgreSQL datasource.
Existing controller routes are retained, including `POST /api/auth/mobile/login`.

## Refinement documentation

Migration 032 adds the trip timeline and incident store. See [the implementation report](documentation/IMPLEMENTATION_REPORT.md), [API additions](documentation/API_DOCUMENTATION.md), [use cases](documentation/USE_CASE_LIST.md) and [run instructions](../README.md).
