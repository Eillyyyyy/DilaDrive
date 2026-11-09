# DilaDrive Customer Flutter Starter

1. Install Flutter SDK and Android Studio / Android SDK.
2. Start PostgreSQL and the Spring Boot API in `../backend/diladrive-api`.
3. Configure the database in the API's `src/main/resources/application.properties`.
4. Open a terminal in this folder and run `flutter pub get`.
5. Start the app with `flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api`.

The default API URL is for an Android emulator. For a physical Android device,
replace `10.0.2.2` with the computer's LAN IP and ensure both devices are on the
same network. `API_BASE_URL` can also be set at build time for other environments.

The login screen verifies customer and driver credentials against PostgreSQL via
`POST /api/auth/mobile/login`. Accounts must already exist in the database.
Photo upload and FCM are not implemented yet.
