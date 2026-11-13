import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../models/customer_notification.dart';
import 'api_http_client.dart';
import 'mobile_session.dart';

class CustomerNotificationService {
  CustomerNotificationService({http.Client? client})
      : _client = client ?? createApiHttpClient();

  final http.Client _client;

  void dispose() => _client.close();

  Future<List<CustomerNotification>> getMyNotifications() async {
    if (!MobileSession.isAuthenticated) {
      throw const CustomerNotificationException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );
    }

    late final http.Response response;
    try {
      response = await _client.get(
        Uri.parse('${AppConfig.apiBaseUrl}/api/notifications/customer/mine'),
        headers: {'Accept': 'application/json', ...MobileSession.headers},
      ).timeout(const Duration(seconds: 15));
    } on TimeoutException {
      throw const CustomerNotificationException(
        'The server took too long to respond. Please try again.',
      );
    } on http.ClientException {
      throw const CustomerNotificationException(
        'Cannot connect to DilaDrive. Check the API connection and try again.',
      );
    }

    if (response.statusCode == 401) {
      MobileSession.clear();
      throw const CustomerNotificationException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );
    }
    if (response.statusCode == 403) {
      throw const CustomerNotificationException(
        'These notifications are only available to customer accounts.',
      );
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw CustomerNotificationException(
        'Notifications could not be loaded (${response.statusCode}). Please try again.',
      );
    }

    final Object? payload;
    try {
      payload = jsonDecode(response.body);
    } on FormatException {
      throw const CustomerNotificationException(
        'The server returned an invalid notifications response.',
      );
    }
    if (payload is! List) {
      throw const CustomerNotificationException(
        'The server returned an invalid notifications response.',
      );
    }
    try {
      return payload.map((item) {
        if (item is! Map<String, dynamic>) {
          throw const FormatException(
              'The server returned an invalid notification.');
        }
        return CustomerNotification.fromJson(item);
      }).toList(growable: false);
    } on FormatException catch (error) {
      throw CustomerNotificationException(error.message);
    }
  }

  Future<void> markAsRead(int notificationId) async {
    if (!MobileSession.isAuthenticated) {
      throw const CustomerNotificationException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );
    }
    late final http.Response response;
    try {
      response = await _client.patch(
        Uri.parse(
          '${AppConfig.apiBaseUrl}/api/notifications/$notificationId/read',
        ),
        headers: {'Accept': 'application/json', ...MobileSession.headers},
      ).timeout(const Duration(seconds: 15));
    } on TimeoutException {
      throw const CustomerNotificationException(
        'The server took too long to respond. Please try again.',
      );
    } on http.ClientException {
      throw const CustomerNotificationException(
        'Cannot connect to DilaDrive. Check the API connection and try again.',
      );
    }
    if (response.statusCode == 401) {
      MobileSession.clear();
      throw const CustomerNotificationException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw CustomerNotificationException(
        response.statusCode == 404
            ? 'This notification is no longer available to your account.'
            : 'The notification could not be updated (${response.statusCode}).',
      );
    }
  }
}

class CustomerNotificationException implements Exception {
  const CustomerNotificationException(this.message,
      {this.requiresLogin = false});

  final String message;
  final bool requiresLogin;

  @override
  String toString() => message;
}
