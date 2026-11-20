import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'dart:async';
import 'dart:convert';
import '../config/app_config.dart';
import 'api_http_client.dart';
import 'mobile_session.dart';

import '../firebase_options.dart';

@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  await Firebase.initializeApp(
    options: DefaultFirebaseOptions.currentPlatform,
  );
}

class PushNotificationService {
  PushNotificationService._();

  static bool _initialized = false;
  static final _updates = StreamController<void>.broadcast();
  static Stream<void> get updates => _updates.stream;
  static String? _registeredToken;
  static Future<void> _registration = Future<void>.value();
  static String? _signedOutCookie;

  static Future<void> syncToken() {
    final cookie = MobileSession.headers['Cookie'];
    if (!_initialized || cookie == null || cookie == _signedOutCookie) {
      return Future<void>.value();
    }
    _registration = _registration.then((_) => _syncToken(cookie));
    return _registration;
  }

  static Future<void> _syncToken(String cookie) async {
    if (cookie == _signedOutCookie || MobileSession.headers['Cookie'] != cookie) {
      return;
    }
    try {
      // Existing installations use registration tokens with Firebase Admin 9.7.
      final messaging = FirebaseMessaging.instance;
      // ignore: deprecated_member_use
      final token = await messaging.getToken().timeout(const Duration(seconds: 5));
      if (cookie == _signedOutCookie ||
          token == null ||
          MobileSession.headers['Cookie'] != cookie) {
        return;
      }
      final client = createApiHttpClient();
      try {
        final response = await client
            .post(Uri.parse('${AppConfig.apiBaseUrl}/api/mobile/push-token'),
                headers: {
                  'Content-Type': 'application/json',
                  'Cookie': cookie,
                },
                body: jsonEncode({'token': token}))
            .timeout(const Duration(seconds: 5));
        if (response.statusCode < 200 || response.statusCode >= 300) {
          debugPrint('DilaDrive FCM registration failed (HTTP ${response.statusCode}).');
          return;
        }
        final previous = _registeredToken;
        _registeredToken = token;
        if (previous != null && previous != token) {
          await _removeRegistration(previous, cookie);
        }
      } finally {
        client.close();
      }
    } catch (error) {
      debugPrint('DilaDrive FCM registration unavailable (${error.runtimeType}).');
    }
  }

  static Future<void> unregister() async {
    final cookie = MobileSession.headers['Cookie'];
    _signedOutCookie = cookie;
    await _registration;
    final token = _registeredToken;
    _registeredToken = null;
    if (token != null && cookie != null) {
      await _removeRegistration(token, cookie);
    }
  }

  static Future<void> _removeRegistration(String token, String cookie) async {
    final client = createApiHttpClient();
    try {
      await client
          .delete(Uri.parse('${AppConfig.apiBaseUrl}/api/mobile/push-token'),
              headers: {
                'Content-Type': 'application/json',
                'Cookie': cookie,
              },
              body: jsonEncode({'token': token}))
          .timeout(const Duration(seconds: 3));
    } catch (_) {/* Local sign-out must still complete. */} finally {
      client.close();
    }
  }

  static Future<void> initialize({
    required void Function(RemoteMessage message) onMessage,
  }) async {
    if (_initialized || kIsWeb) return;

    final platform = defaultTargetPlatform;
    if (platform != TargetPlatform.android &&
        platform != TargetPlatform.iOS &&
        platform != TargetPlatform.macOS) {
      return;
    }

    try {
      await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
      FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);
      final messaging = FirebaseMessaging.instance;
      final permission = await messaging.requestPermission(
        alert: true,
        badge: true,
        sound: true,
      );
      if (permission.authorizationStatus == AuthorizationStatus.denied) return;
      _initialized = true;
      void handleMessage(RemoteMessage message) {
        _updates.add(null);
        onMessage(message);
      }

      FirebaseMessaging.onMessage.listen(handleMessage);
      FirebaseMessaging.onMessageOpenedApp.listen(handleMessage);
      // ignore: deprecated_member_use
      messaging.onTokenRefresh.listen(
        (_) => unawaited(syncToken()),
        onError: (Object error) => debugPrint(
          'DilaDrive FCM token refresh unavailable (${error.runtimeType}).',
        ),
      );
      unawaited(syncToken());
      final initialMessage = await messaging.getInitialMessage();
      if (initialMessage != null) onMessage(initialMessage);
    } catch (error) {
      debugPrint('DilaDrive FCM initialization unavailable (${error.runtimeType}).');
    }
  }
}
