
import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

class AuthException implements Exception {
  final String message;

  AuthException(this.message);

  @override
  String toString() => message;
}

// ========================================
// AUTHENTICATED USER MODEL
// ========================================

class AuthUser {
  final int id;
  final String name;
  final String email;
  final String role;

  AuthUser({
    required this.id,
    required this.name,
    required this.email,
    required this.role,
  });

  factory AuthUser.fromJson(Map<String, dynamic> json) {
    final rawId = json['id'];

    final int? parsedId = rawId is int
        ? rawId
        : rawId is num
            ? rawId.toInt()
            : int.tryParse(rawId?.toString() ?? '');

    if (parsedId == null) {
      throw const FormatException(
        'Missing or invalid user ID.',
      );
    }

    return AuthUser(
      id: parsedId,
      name: json['name']?.toString() ?? '',
      email: json['email']?.toString() ?? '',
      role: json['role']?.toString() ?? '',
    );
  }
}

// ========================================
// AUTH SERVICE
// ========================================

class AuthService {
  final http.Client _client;
  final bool _ownsClient;
  final String? _baseUrlOverride;

  AuthService({
    http.Client? client,
    String? baseUrl,
  })  : _client = client ?? http.Client(),
        _ownsClient = client == null,
        _baseUrlOverride = baseUrl;

  // ========================================
  // BACKEND URL
  // ========================================

  String get baseUrl {
    if (_baseUrlOverride != null) {
      return _baseUrlOverride;
    }

    // Flutter Web - Chrome
    if (kIsWeb) {
      return 'http://localhost:8080';
    }

    // Android Emulator
    if (defaultTargetPlatform == TargetPlatform.android) {
      return 'http://10.0.2.2:8080';
    }

    // Desktop / other local platforms
    return 'http://localhost:8080';
  }

  // ========================================
  // CUSTOMER / DRIVER LOGIN
  // ========================================

  Future<AuthUser> login({
    required String email,
    required String password,
    required String role,
  }) async {
    final cleanEmail = email.trim();
    final cleanRole = role.trim().toUpperCase();

    if (cleanEmail.isEmpty) {
      throw AuthException(
        'Please enter your email address.',
      );
    }

    if (password.isEmpty) {
      throw AuthException(
        'Please enter your password.',
      );
    }

    if (cleanRole != 'CUSTOMER' &&
        cleanRole != 'DRIVER') {
      throw AuthException(
        'Invalid login role.',
      );
    }

    final uri = Uri.parse(
      '$baseUrl/api/auth/mobile/login',
    );

    try {
      debugPrint('DilaDrive Mobile Login: $uri');
      debugPrint('Login role: $cleanRole');

      final response = await _client
          .post(
            uri,
            headers: {
              'Content-Type': 'application/json',
              'Accept': 'application/json',
            },
            body: jsonEncode({
              'email': cleanEmail,
              'password': password,
              'role': cleanRole,
            }),
          )
          .timeout(
            const Duration(seconds: 20),
          );

      debugPrint(
        'Mobile Login HTTP Status: ${response.statusCode}',
      );

      // ========================================
      // LOGIN SUCCESS
      // ========================================

      if (response.statusCode == 200) {
        dynamic decoded;

        try {
          decoded = jsonDecode(response.body);
        } on FormatException {
          throw AuthException(
            'Invalid JSON response from server.',
          );
        }

        if (decoded is! Map<String, dynamic>) {
          throw AuthException(
            'Invalid login response format.',
          );
        }

        try {
          final user = AuthUser.fromJson(decoded);

          if (user.role.toUpperCase() != cleanRole) {
            throw AuthException(
              'Account role does not match selected login type.',
            );
          }

          return user;
        } on FormatException catch (e) {
          throw AuthException(
            'Invalid user data from server: ${e.message}',
          );
        }
      }

      // ========================================
      // INVALID LOGIN
      // ========================================

      if (response.statusCode == 401) {
        throw AuthException(
          'Invalid email or password.',
        );
      }

      if (response.statusCode == 403) {
        throw AuthException(
          'Access denied. Please check your account status.',
        );
      }

      if (response.statusCode == 404) {
        throw AuthException(
          'Mobile login API endpoint not found.',
        );
      }

      if (response.statusCode >= 500) {
        throw AuthException(
          'Server error (${response.statusCode}). '
          'Please check Spring Boot terminal.',
        );
      }

      // ========================================
      // OTHER SERVER ERRORS
      // ========================================

      String errorMessage =
          'Login failed (HTTP ${response.statusCode}).';

      try {
        final decoded = jsonDecode(response.body);

        if (decoded is Map &&
            decoded['message'] != null) {
          errorMessage = decoded['message'].toString();
        }
      } catch (_) {
        if (response.body.trim().isNotEmpty) {
          errorMessage = response.body.trim();
        }
      }

      throw AuthException(errorMessage);

    } on AuthException {
      rethrow;

    } on TimeoutException {
      throw AuthException(
        'Connection timed out. '
        'Please check if Spring Boot is running on port 8080.',
      );

    } on http.ClientException catch (e) {
      debugPrint('HTTP Client Error: $e');

      throw AuthException(
        'Cannot connect to Spring Boot. '
        'Check server connection or browser CORS settings.',
      );

    } catch (e) {
      debugPrint('Unexpected Login Error: $e');

      throw AuthException(
        'Login request failed. '
        'Please check Chrome Console and Spring Boot logs.',
      );
    }
  }

  // ========================================
  // DISPOSE HTTP CLIENT
  // ========================================

  void dispose() {
    if (_ownsClient) {
      _client.close();
    }
  }
}
