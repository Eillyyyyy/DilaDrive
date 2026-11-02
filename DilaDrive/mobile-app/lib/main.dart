import 'package:flutter/material.dart';

import 'screens/auth/login.dart';
import 'screens/auth/register.dart';

void main() {
  runApp(const DilaDriveApp());
}

class DilaDriveApp extends StatelessWidget {
  const DilaDriveApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'DilaDrive',

      // Page pertama
      home: const LoginPage(),

      // Routes
      routes: {
        '/register': (context) => const RegisterPage(),
      },
    );
  }
}