import 'dart:async';

import 'package:flutter/material.dart';
import 'package:firebase_messaging/firebase_messaging.dart';

import 'theme/app_theme.dart';
import 'screens/auth/login.dart';
import 'screens/auth/register.dart';
import 'services/push_notification_service.dart';

final GlobalKey<ScaffoldMessengerState> scaffoldMessengerKey =
    GlobalKey<ScaffoldMessengerState>();

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const DilaDriveApp());
  unawaited(PushNotificationService.initialize(onMessage: _showPushMessage));
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
