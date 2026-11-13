import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../models/customer_booking.dart';
import 'api_http_client.dart';
import 'mobile_session.dart';

class CustomerBookingService {
  CustomerBookingService({http.Client? client})
      : _client = client ?? createApiHttpClient();

  final http.Client _client;

  void dispose() => _client.close();

  Future<List<CustomerBooking>> getMyBookings() async {
    if (!MobileSession.isAuthenticated) {
      throw const CustomerBookingException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );
    }

    late final http.Response response;
    try {
      response = await _client.get(
        Uri.parse('${AppConfig.apiBaseUrl}/api/bookings/mine'),
        headers: {
          'Accept': 'application/json',
          ...MobileSession.headers,
        },
      ).timeout(const Duration(seconds: 15));
    } on TimeoutException {
      throw const CustomerBookingException(
        'The server took too long to respond. Please try again.',
      );
    } on http.ClientException {
      throw const CustomerBookingException(
        'Cannot connect to DilaDrive. Check the API connection and try again.',
      );
    }

    if (response.statusCode == 401) {
      MobileSession.clear();
      throw const CustomerBookingException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );
    }
    if (response.statusCode == 403) {
      throw const CustomerBookingException(
        'This booking list is only available to customer accounts.',
      );
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw CustomerBookingException(
        'Bookings could not be loaded (${response.statusCode}). Please try again.',
      );
    }

    final Object? payload;
    try {
      payload = jsonDecode(response.body);
    } on FormatException {
      throw const CustomerBookingException(
        'The server returned an invalid bookings response.',
      );
    }
    if (payload is! List) {
      throw const CustomerBookingException(
        'The server returned an invalid bookings response.',
      );
    }

    try {
      return payload.map((item) {
        if (item is! Map<String, dynamic>) {
          throw const FormatException(
            'The server returned an invalid booking record.',
          );
        }
        return CustomerBooking.fromJson(item);
      }).toList(growable: false);
    } on FormatException catch (exception) {
      throw CustomerBookingException(exception.message);
    }
  }

  Future<void> cancelBooking(int bookingId) async {
    if (!MobileSession.isAuthenticated) {
      throw const CustomerBookingException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );
    }

    late final http.Response response;
    try {
      response = await _client.post(
        Uri.parse('${AppConfig.apiBaseUrl}/api/bookings/$bookingId/cancel'),
        headers: {
          'Accept': 'application/json',
          ...MobileSession.headers,
        },
      ).timeout(const Duration(seconds: 15));
    } on TimeoutException {
      throw const CustomerBookingException(
        'The server took too long to respond. Please try again.',
      );
    } on http.ClientException {
      throw const CustomerBookingException(
        'Cannot connect to DilaDrive. Check the API connection and try again.',
      );
    }

    if (response.statusCode == 401) {
      MobileSession.clear();
      throw const CustomerBookingException(
        'Your sign-in session has expired. Please sign in again.',
        requiresLogin: true,
      );
    }
    if (response.statusCode == 404) {
      throw const CustomerBookingException(
        'This booking is not available to your account.',
      );
    }
    if (response.statusCode == 409) {
      throw CustomerBookingException(
        _serverMessage(response.body) ??
            'This booking can no longer be cancelled by the customer.',
      );
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw CustomerBookingException(
        'The booking could not be cancelled (${response.statusCode}).',
      );
    }
  }

  String? _serverMessage(String body) {
    try {
      final payload = jsonDecode(body);
      if (payload is Map<String, dynamic> && payload['message'] is String) {
        return payload['message'] as String;
      }
    } on FormatException {
      return null;
    }
    return null;
  }
}

class CustomerBookingException implements Exception {
  const CustomerBookingException(this.message, {this.requiresLogin = false});

  final String message;
  final bool requiresLogin;

  @override
  String toString() => message;
}
