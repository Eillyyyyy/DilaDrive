import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../models/driver_profile.dart';
import 'api_http_client.dart';
import 'mobile_session.dart';

class DriverProfileService {
  DriverProfileService({http.Client? client})
      : _client = client ?? createApiHttpClient();

  final http.Client _client;
  void dispose() => _client.close();

  Future<DriverProfile> getProfile() async {
    final response = await _request(() => _client.get(
          Uri.parse('${AppConfig.apiBaseUrl}/api/drivers/me'),
          headers: {'Accept': 'application/json', ...MobileSession.headers},
        ));
    return _parse(response.body);
  }

  Future<DriverProfile> updateProfile({
    required String name,
    required String email,
    required String phone,
  }) async {
    final response = await _request(() => _client.put(
          Uri.parse('${AppConfig.apiBaseUrl}/api/drivers/me'),
          headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            ...MobileSession.headers,
          },
          body: jsonEncode({
            'name': name.trim(),
            'email': email.trim(),
            'phone': phone.trim(),
          }),
        ));
    return _parse(response.body);
  }

  DriverProfile _parse(String body) {
    try {
      final payload = jsonDecode(body);
      if (payload is! Map<String, dynamic>) {
        throw const FormatException('Invalid profile response.');
      }
      return DriverProfile.fromJson(payload);
    } on FormatException catch (error) {
      throw DriverProfileException(error.message);
    }
  }

  Future<http.Response> _request(Future<http.Response> Function() send) async {
    if (!MobileSession.isAuthenticated) {
      throw const DriverProfileException(
          'Your sign-in session has expired. Please sign in again.',
          requiresLogin: true);
    }
    late final http.Response response;
    try {
      response = await send().timeout(const Duration(seconds: 15));
    } on TimeoutException {
      throw const DriverProfileException(
          'The server took too long to respond. Please try again.');
    } on http.ClientException {
      throw const DriverProfileException(
          'Cannot connect to DilaDrive. Check the API connection and try again.');
    }
    if (response.statusCode == 401) {
      MobileSession.clear();
      throw const DriverProfileException(
          'Your sign-in session has expired. Please sign in again.',
          requiresLogin: true);
    }
    if (response.statusCode == 403) {
      throw const DriverProfileException(
          'This profile is only available to the signed-in driver.');
    }
    if (response.statusCode == 409) {
      throw const DriverProfileException(
          'That email address is already used by another account.');
    }
    if (response.statusCode == 400) {
      throw const DriverProfileException(
          'Check the name, email and phone number, then try again.');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw DriverProfileException(
          'The profile request failed (${response.statusCode}).');
    }
    return response;
  }
}

class DriverProfileException implements Exception {
  const DriverProfileException(this.message, {this.requiresLogin = false});
  final String message;
  final bool requiresLogin;
  @override
  String toString() => message;
}
