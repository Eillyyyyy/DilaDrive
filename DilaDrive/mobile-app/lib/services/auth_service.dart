import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';

class AuthService {
  AuthService({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;

  void dispose() {
    _client.close();
  }

  Future<AuthenticatedUser> login({
    required String email,
    required String password,
    required String role,
  }) async {
    late final http.Response response;
    try {
      response = await _client
          .post(
            Uri.parse('${AppConfig.apiBaseUrl}/auth/mobile/login'),
            headers: const {'Content-Type': 'application/json'},
            body: jsonEncode({
              'email': email.trim(),
              'password': password,
              'role': role,
            }),
          )
          .timeout(const Duration(seconds: 15));
    } on TimeoutException {
      throw const AuthException(
        'The server took too long to respond. Please try again.',
      );
    } on http.ClientException {
      throw const AuthException(
        'Cannot connect to DilaDrive. Check that the API and database are running and verify the API URL.',
      );
    }

    if (response.statusCode == 401) {
      throw const AuthException('Invalid email or password.');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AuthException(_serverErrorMessage(response));
    }

    final Object? payload;
    try {
      payload = jsonDecode(response.body);
    } on FormatException {
      throw const AuthException('The server returned an invalid response.');
    }
    if (payload is! Map<String, dynamic>) {
      throw const AuthException('The server returned an invalid response.');
    }

    final userId = payload['id'];
    final name = payload['name'];
    final responseEmail = payload['email'];
    final responseRole = payload['role'];
    if (userId == null ||
        name is! String ||
        responseEmail is! String ||
        responseRole is! String) {
      throw const AuthException('The server returned incomplete account data.');
    }

    return AuthenticatedUser(
      id: userId.toString(),
      name: name,
      email: responseEmail,
      role: responseRole,
    );
  }

  String _serverErrorMessage(http.Response response) {
    try {
      final payload = jsonDecode(response.body);
      if (payload is String && payload.isNotEmpty) {
        return payload;
      }
      if (payload is Map<String, dynamic>) {
        final message = payload['message'] ?? payload['error'];
        if (message is String && message.isNotEmpty) {
          return message;
        }
      }
    } on FormatException {
      // Use a safe generic message for non-JSON server errors.
    }
    return 'Login failed (${response.statusCode}). Please try again later.';
  }
}

class AuthenticatedUser {
  const AuthenticatedUser({
    required this.id,
    required this.name,
    required this.email,
    required this.role,
  });

  final String id;
  final String name;
  final String email;
  final String role;
}

class AuthException implements Exception {
  const AuthException(this.message);

  final String message;

  @override
  String toString() => message;
}
