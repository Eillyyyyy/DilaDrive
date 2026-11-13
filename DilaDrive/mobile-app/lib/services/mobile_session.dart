import 'package:flutter/foundation.dart';

class MobileSession {
  MobileSession._();

  static String? _sessionCookie;
  static bool _webSessionAuthenticated = false;

  static Map<String, String> get headers => kIsWeb
      ? const {}
      : {
          if (_sessionCookie != null) 'Cookie': _sessionCookie!,
        };

  static bool get isAuthenticated =>
      kIsWeb ? _webSessionAuthenticated : _sessionCookie != null;

  static void storeResponseCookies(Map<String, String> headers) {
    // Browsers hide Set-Cookie from Dart. BrowserClient stores and sends it
    // automatically when withCredentials is enabled.
    if (kIsWeb) {
      _webSessionAuthenticated = true;
      return;
    }

    for (final entry in headers.entries) {
      if (entry.key.toLowerCase() != 'set-cookie') continue;

      final cookie = entry.value.split(';').first.trim();
      if (cookie.startsWith('JSESSIONID=') && cookie.length > 11) {
        _sessionCookie = cookie;
        return;
      }
    }
  }

  static void clear() {
    _sessionCookie = null;
    _webSessionAuthenticated = false;
  }
}
