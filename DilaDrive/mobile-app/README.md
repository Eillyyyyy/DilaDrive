# DilaDrive Flutter App

1. Install Flutter SDK and Android Studio / Android SDK.
2. Start PostgreSQL and the Spring Boot API in `../backend/diladrive-api`.
3. Configure the database through the backend environment variables documented in
   `../backend/diladrive-api/README.md`.
4. Open a terminal in this folder and run `flutter pub get`.
5. Start the app:
   - Android emulator: `flutter run` (the app defaults to `10.0.2.2:8080`)
   - Physical Android device: use the computer's LAN IP, for example
     `flutter run --dart-define=API_BASE_URL=http://192.168.1.20:8080`

`lib/config/app_config.dart` defaults to `http://10.0.2.2:8080` on Android and
`http://localhost:8080` on desktop. Set `API_BASE_URL` for a physical device or
another deployment. It can point to the API root with or without a trailing
`/api`; the app normalizes the suffix before adding endpoint paths. The API
must be bound to a reachable interface, and the host firewall must allow
connections to port 8080. Release builds should use an HTTPS API endpoint;
plain HTTP is intended for local development only.

The login screen verifies customer and driver credentials against PostgreSQL via
`POST /api/auth/mobile/login`. Accounts must already exist in the database.
The app uses the same Spring Boot API and PostgreSQL datasource as the React
Admin web application. Driver verification photo upload and viewing are
available while an assignment is active. Push notifications through FCM are
not implemented; in-app notifications are loaded from the API.
