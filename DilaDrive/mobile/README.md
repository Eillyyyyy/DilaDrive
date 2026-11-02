# DilaDrive Mobile (Flutter, Android)

One app for **customers** and **drivers**. The JWT role returned by `/api/v1/auth/login` decides which shell opens (decision D2). Admin accounts are rejected on mobile.

## Create the project (first time, on a machine with Flutter installed)
```bash
cd diladrive
flutter create --org com.diladrive --project-name diladrive_mobile --platforms android mobile_tmp
# move the generated files into mobile/ without overwriting lib/core/theme
cp -rn mobile_tmp/. mobile/ && rm -rf mobile_tmp
cd mobile
flutter pub add flutter_riverpod dio go_router flutter_secure_storage google_fonts image_picker file_picker url_launcher open_filex intl
```
Then in `lib/main.dart` use `theme: AppTheme.light()` from `core/theme/app_theme.dart`.

## API base URL
- Android emulator → host machine: `http://10.0.2.2:8080`
- Real phone on same Wi-Fi: `http://<your-PC-LAN-IP>:8080` (and allow it through the firewall)

Login request must send `"client": "MOBILE"`:
```json
{ "identifier": "driver1", "password": "••••••••", "client": "MOBILE" }
```

## Planned structure
```
lib/
  core/        theme, api client (dio + JWT interceptor), router, secure storage
  features/
    auth/      login, register
    customer/  home, new booking, my bookings, payments, documents, notifications
    driver/    dashboard, schedule, current trip (milestone stepper), history
```
