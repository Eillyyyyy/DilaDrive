# DilaDrive

Integrated Transport Management System for Fadilah Transportation.

| Folder | What | Stack |
|--------|------|-------|
| `backend/` | REST API | Java 21, Spring Boot 3, PostgreSQL, Flyway, JWT |
| `admin-web/` | Admin dashboard | React + Vite + TypeScript + MUI |
| `mobile/` | Customer + driver app (one app, role-based) | Flutter (Android) |
| `docs/` | Blueprint and ERD v2 | Markdown |

Design decisions live in `docs/DILADRIVE_DECISIONS_AND_ERD_V2.md` (it overrides parts of the blueprint).

## Quick start

### 1. Database
```bash
createdb diladrive            # or create it in pgAdmin
```
Default dev connection (override with env vars): `jdbc:postgresql://localhost:5432/diladrive`, user `postgres`, password `postgres`.

### 2. Backend
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
- Flyway creates all tables and seed data on first start.
- A first admin is created from `DILADRIVE_ADMIN_EMAIL` / `DILADRIVE_ADMIN_PASSWORD` (dev defaults: `admin@diladrive.local` / `ChangeMe123!`). Change it immediately.
- Swagger UI: http://localhost:8080/swagger-ui.html

### 3. Admin web
```bash
cd admin-web
npm create vite@latest . -- --template react-ts   # first time only, keep src/theme
npm install @mui/material @emotion/react @emotion/styled react-router-dom @tanstack/react-query axios
npm run dev
```

### 4. Mobile
See `mobile/README.md`. Android emulator reaches the backend at `http://10.0.2.2:8080`.

## Auth endpoints (v1)
| Method | Path | Who |
|--------|------|-----|
| POST | `/api/v1/auth/register` | public (creates CUSTOMER, status PENDING) |
| POST | `/api/v1/auth/login` | public (returns JWT + role + status) |
| GET | `/api/v1/auth/me` | any logged-in user |
