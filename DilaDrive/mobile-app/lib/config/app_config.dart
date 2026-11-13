import 'package:flutter/foundation.dart';

class AppConfig {
  static const String _configuredApiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
  );

  static String get apiBaseUrl {
    final configured = _configuredApiBaseUrl.trim();
    final defaultUrl = defaultTargetPlatform == TargetPlatform.android
        ? 'http://10.0.2.2:8080'
        : 'http://localhost:8080';

    // Android emulators reach the host machine through 10.0.2.2. Physical
    // devices should pass their host's LAN address with API_BASE_URL.
    return (configured.isEmpty ? defaultUrl : configured)
        .replaceFirst(RegExp(r'/+$'), '')
        .replaceFirst(RegExp(r'/api$', caseSensitive: false), '');
  }
}
