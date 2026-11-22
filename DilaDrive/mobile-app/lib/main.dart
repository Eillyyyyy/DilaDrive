import 'dart:async';

import 'package:flutter/material.dart';
import 'package:firebase_messaging/firebase_messaging.dart';

import 'theme/app_theme.dart';
import 'screens/auth/login.dart';
import 'screens/auth/register.dart';
import 'services/mobile_session.dart';
import 'services/push_notification_service.dart';

final GlobalKey<ScaffoldMessengerState> scaffoldMessengerKey =
    GlobalKey<ScaffoldMessengerState>();
final GlobalKey<NavigatorState> _navigatorKey = GlobalKey<NavigatorState>();

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  MobileSession.onDriverDeactivated = _returnDeactivatedDriverToLogin;
  runApp(const DilaDriveApp());
  unawaited(PushNotificationService.initialize(onMessage: _showPushMessage));
}

void _returnDeactivatedDriverToLogin(String message) {
  _navigatorKey.currentState?.pushAndRemoveUntil<void>(
    MaterialPageRoute<void>(builder: (_) => const LoginPage()),
    (_) => false,
  );
  scaffoldMessengerKey.currentState
    ?..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));
}

void _showPushMessage(RemoteMessage message) {
  final notification = message.notification;
  final title = notification?.title;
  final body = notification?.body;
  final text = [title, body]
      .whereType<String>()
      .where((part) => part.trim().isNotEmpty)
      .join(': ');
  if (text.isEmpty) return;

  scaffoldMessengerKey.currentState
    ?..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(text)));
}

class DilaDriveApp extends StatelessWidget {
  const DilaDriveApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      navigatorKey: _navigatorKey,
      scaffoldMessengerKey: scaffoldMessengerKey,
      debugShowCheckedModeBanner: false,
      title: 'DilaDrive',
      theme: AppTheme.lightTheme,

      // Page pertama
      home: const LoginPage(),

      // Routes
      routes: {
        '/register': (context) => const RegisterPage(),
      },
    );
  }
}
