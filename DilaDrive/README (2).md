# DilaDrive — FYP Starter Project

This ZIP contains a runnable starter scaffold for the DilaDrive FYP:
- `web-admin/`: React + Vite + Tailwind admin UI with demo data
- `backend/diladrive-api/`: Spring Boot REST API starter
- `mobile-customer/`: Flutter customer app starter
- `mobile-driver/`: Flutter driver app starter
- `database/schema/`: PostgreSQL starter schema
- `documentation/`: setup notes and API outline

## Important
This is a starter prototype, not a fully completed production system. The admin UI uses dummy data. JWT authentication, database persistence, file uploads, FCM, payments, and complete business rules still need implementation and testing.

## Requirements
- Node.js LTS and npm
- Java 17+
- Maven
- Flutter SDK (for mobile apps)
- PostgreSQL (optional for the current backend starter)

## Start Admin Web
```bash
cd web-admin
npm install
npm run dev
```
Open the URL shown by Vite, usually http://localhost:5173.

## Start Spring Boot API
```bash
cd backend/diladrive-api
mvn spring-boot:run
```
API health endpoint: `GET http://localhost:8080/api/health`

## Flutter apps
Run these from each app folder after installing Flutter:
```bash
flutter pub get
flutter run
```
For a real Android device, update the API base URL in `lib/config/app_config.dart` to your computer's LAN IP. `localhost` on a phone refers to the phone itself.

## PostgreSQL
Create a database named `diladrive`, then configure environment variables:
- `DB_URL` (example: `jdbc:postgresql://localhost:5432/diladrive`)
- `DB_USERNAME`
- `DB_PASSWORD`

The backend defaults to an H2 in-memory database so the starter can run without PostgreSQL setup. Change `application.properties` when ready to connect PostgreSQL.

## Suggested next steps
1. Confirm the project builds on your computer.
2. Replace demo data with API calls.
3. Implement user authentication and role-based authorization.
4. Add JPA entities/repositories for the ERD tables.
5. Add secure upload storage and FCM credentials via environment variables (never commit service-account keys).
