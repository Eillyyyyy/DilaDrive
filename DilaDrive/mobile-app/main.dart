import 'package:flutter/material.dart';
import 'lib/screens/auth/login.dart';


void main() {
  runApp(const DilaDriveApp());
}

class DilaDriveApp extends StatelessWidget {
  const DilaDriveApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: LoginPage(),
    );
  }
}