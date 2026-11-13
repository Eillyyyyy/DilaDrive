import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../models/customer_profile.dart';
import 'api_http_client.dart';
import 'mobile_session.dart';

class CustomerProfileService {
  CustomerProfileService({http.Client? client})
      : _client = client ?? createApiHttpClient();

  final http.Client _client;

  void dispose() => _client.close();

  Future<CustomerProfile> getMyProfile() async {
    final response = await _request(
      () => _client.get(
        Uri.parse('${AppConfig.apiBaseUrl}/api/customers/me'),
        headers: {'Accept': 'application/json', ...MobileSession.headers},
      ),
    );
    return _parseProfile(response);
  }

  Future<CustomerProfile> updateMyProfile({
    required String name,
    required String email,
    String? phone,
    String? companyName,
    String? companyRegistrationNumber,
    String? profileImage,
  }) async {
    final response = await _request(
      () => _client.put(
        Uri.parse('${AppConfig.apiBaseUrl}/api/customers/me'),
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
          ...MobileSession.headers,
        },
        body: jsonEncode({
          'name': name.trim(),
          'email': email.trim(),
          'phone': phone?.trim(),
          'companyName': companyName?.trim(),
          'companyRegistrationNumber': companyRegistrationNumber?.trim(),
          'profileImage': profileImage,
        }),
      ),
    );
    return _parseProfile(response);
  }

  Future<http.Response> _request(Future<http.Response> Function() send) async {
    if (!MobileSession.isAuthenticated) {
      throw const CustomerProfileException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );
    }
    late final http.Response response;
    try {
      response = await send().timeout(const Duration(seconds: 15));
    } on TimeoutException {
      throw const CustomerProfileException(
        'The server took too long to respond. Please try again.',
      );
    } on http.ClientException {
      throw const CustomerProfileException(
        'Cannot connect to DilaDrive. Check the API connection and try again.',
      );
    }
    if (response.statusCode == 401) {
      MobileSession.clear();
      throw const CustomerProfileException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );
    }
    if (response.statusCode == 403) {
      throw CustomerProfileException(
        _serverMessage(response.body) ??
            'Profile details are only available to customer accounts.',
      );
    }
    if (response.statusCode == 409) {
      throw CustomerProfileException(
        _serverMessage(response.body) ??
            'That email address is already used by another account.',
      );
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw CustomerProfileException(
        _serverMessage(response.body) ??
            'Your profile could not be saved or loaded (${response.statusCode}).',
      );
    }
    return response;
  }

  CustomerProfile _parseProfile(http.Response response) {
    try {
      final payload = jsonDecode(response.body);
      if (payload is! Map<String, dynamic>) {
        throw const FormatException('Invalid profile response.');
      }
      return CustomerProfile.fromJson(payload);
    } on FormatException catch (error) {
      throw CustomerProfileException(error.message);
    }
  }

  String? _serverMessage(String body) {
    try {
      final payload = jsonDecode(body);
      if (payload is Map<String, dynamic> && payload['message'] is String) {
        final message = (payload['message'] as String).trim();
        return message.isEmpty ? null : message;
      }
    } on FormatException {
      return null;
    }
    return null;
  }
}

class CustomerProfileException implements Exception {
  const CustomerProfileException(this.message, {this.requiresLogin = false});

  final String message;
  final bool requiresLogin;

  @override
  String toString() => message;
}
