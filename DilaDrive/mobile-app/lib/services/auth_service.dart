import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../models/mobile_user.dart';
import 'api_http_client.dart';
import 'auth_exception.dart';
import 'mobile_session.dart';

export '../models/mobile_user.dart';
export 'auth_exception.dart';

class AuthService {
  AuthService({http.Client? client}) : _client = client ?? createApiHttpClient();

  final http.Client _client;

  void dispose() {
    _client.close();
  }

  Future<void> logout() async {
    try {
      if (MobileSession.isAuthenticated) {
        await _client
            .post(
              Uri.parse('${AppConfig.apiBaseUrl}/api/auth/logout'),
              headers: MobileSession.headers,
            )
            .timeout(const Duration(seconds: 5));
      }
    } catch (_) {
      // Always end the local session, even if the server cannot be reached.
    } finally {
      MobileSession.clear();
    }
  }

  Future<MobileUser> login({
    required String email,
    required String password,
    required String role,
  }) async {
    late final http.Response response;
    try {
      response = await _client
          .post(
            Uri.parse('${AppConfig.apiBaseUrl}/api/auth/mobile/login'),
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
      MobileSession.clear();
      throw const AuthException('Invalid email or password');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AuthException(_serverErrorMessage(response));
    }

    MobileSession.storeResponseCookies(response.headers);

    final Object? payload;
    try {
      payload = jsonDecode(response.body);
    } on FormatException {
      throw const AuthException('The server returned an invalid response.');
    }
    if (payload is! Map<String, dynamic>) {
      throw const AuthException('The server returned an invalid response.');
    }

    try {
      return MobileUser.fromJson(payload);
    } on FormatException catch (exception) {
      throw AuthException(exception.message);
    }
  }

  Future<void> registerCustomer({
    required String name,
    required String email,
    required String phone,
    required String type,
    String? companyName,
    String? companyRegistrationNumber,
    required String password,
  }) async {
    late final http.Response response;
    try {
      response = await _client
          .post(
            Uri.parse('${AppConfig.apiBaseUrl}/api/auth/mobile/register'),
            headers: const {'Content-Type': 'application/json'},
            body: jsonEncode({
              'name': name.trim(),
              'email': email.trim(),
              'phone': phone.trim().isEmpty ? null : phone.trim(),
              'type': type,
              'companyName': companyName?.trim(),
              'companyRegistrationNumber': companyRegistrationNumber?.trim(),
              'password': password,
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

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AuthException(_serverErrorMessage(response));
    }
  }

  String _serverErrorMessage(http.Response response) {
    try {
      final payload = jsonDecode(response.body);
      if (payload is String && payload.isNotEmpty) {
        return payload;
      }
      if (payload is Map<String, dynamic>) {
        final message =
            payload['message'] ?? payload['error'] ?? payload['detail'];
        if (message is String && message.isNotEmpty) {
          return message;
        }
      }
    } on FormatException {
      final text = response.body.trim();
      if (text.isNotEmpty) return text;
    }
    return 'Login failed (${response.statusCode}). Please try again later.';
  }
}
