import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../models/customer_notification.dart';
import 'api_http_client.dart';
import 'mobile_session.dart';

class DriverNotificationService {
  DriverNotificationService({http.Client? client})
      : _client = client ?? createApiHttpClient();

  final http.Client _client;

  void dispose() => _client.close();

  Future<List<CustomerNotification>> getNotifications(int driverId) async {
    final response = await _request(() => _client.get(
          Uri.parse(
              '${AppConfig.apiBaseUrl}/api/notifications/driver/$driverId'),
          headers: {'Accept': 'application/json', ...MobileSession.headers},
        ));
    final Object? payload;
    try {
      payload = jsonDecode(response.body);
    } on FormatException {
      throw const DriverNotificationException(
          'The server returned an invalid notifications response.');
    }
    if (payload is! List) {
      throw const DriverNotificationException(
          'The server returned an invalid notifications response.');
    }
    try {
      return payload.map((item) {
        if (item is! Map<String, dynamic>) {
          throw const FormatException('Invalid notification.');
        }
        return CustomerNotification.fromJson(item);
      }).toList(growable: false);
    } on FormatException catch (error) {
      throw DriverNotificationException(error.message);
    }
  }

  Future<void> markAsRead(int notificationId) async {
    await _request(() => _client.patch(
          Uri.parse(
              '${AppConfig.apiBaseUrl}/api/notifications/$notificationId/read'),
          headers: {'Accept': 'application/json', ...MobileSession.headers},
        ));
  }

  Future<http.Response> _request(Future<http.Response> Function() send) async {
    if (!MobileSession.isAuthenticated) {
      throw const DriverNotificationException(
          'Your sign-in session has expired. Please sign in again.',
          requiresLogin: true);
    }
    late final http.Response response;
    try {
      response = await send().timeout(const Duration(seconds: 15));
    } on TimeoutException {
      throw const DriverNotificationException(
          'The server took too long to respond. Please try again.');
    } on http.ClientException {
      throw const DriverNotificationException(
          'Cannot connect to DilaDrive. Check the API connection and try again.');
    }
    if (response.statusCode == 401) {
      MobileSession.clear();
      throw const DriverNotificationException(
          'Your sign-in session has expired. Please sign in again.',
          requiresLogin: true);
    }
    if (response.statusCode == 403) {
      throw const DriverNotificationException(
          'These notifications are only available to the signed-in driver.');
    }
    if (response.statusCode == 404) {
      throw const DriverNotificationException(
          'This notification is no longer available to your account.');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw DriverNotificationException(
          'The notification request failed (${response.statusCode}).');
    }
    return response;
  }
}

class DriverNotificationException implements Exception {
  const DriverNotificationException(this.message, {this.requiresLogin = false});

  final String message;
  final bool requiresLogin;

  @override
  String toString() => message;
}
